package org.dynmap.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.util.LinkedList;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.dynmap.Log;
import org.dynmap.MapType.ImageFormat;
import org.dynmap.debug.Debug;
/**
 * Implements soft-locks for prevent concurrency issues with file updates
 */
public class ImageIOManager {
    public static String preUpdateCommand = null;
    public static String postUpdateCommand = null;
    private static Object imageioLock = new Object();
    
    public static BufferOutputStream imageIOEncode(BufferedImage img, ImageFormat fmt) {
        BufferOutputStream bos = new BufferOutputStream();

        synchronized(imageioLock) {
            try {
                ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
                if(fmt.getFileExt().equals("jpg")) {
                    WritableRaster raster = img.getRaster();
                    WritableRaster newRaster = raster.createWritableChild(0, 0, img.getWidth(),
                            img.getHeight(), 0, 0, new int[] {0, 1, 2});
                    DirectColorModel cm = (DirectColorModel)img.getColorModel();
                    DirectColorModel newCM = new DirectColorModel(cm.getPixelSize(),
                            cm.getRedMask(), cm.getGreenMask(), cm.getBlueMask());
                    // now create the new buffer that is used ot write the image:
                    BufferedImage rgbBuffer = new BufferedImage(newCM, newRaster, false, null);

                    // Find a jpeg writer
                    ImageWriter writer = null;
                    Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpg");
                    if (iter.hasNext()) {
                        writer = iter.next();
                    }
                    if(writer == null) {
                        Log.severe("No JPEG ENCODER - Java VM does not support JPEG encoding");
                        return null;
                    }
                    ImageWriteParam iwp = writer.getDefaultWriteParam();
                    iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    iwp.setCompressionQuality(fmt.getQuality());

                    ImageOutputStream ios;
                    ios = ImageIO.createImageOutputStream(bos);
                    writer.setOutput(ios);

                    writer.write(null, new IIOImage(rgbBuffer, null, null), iwp);
                    writer.dispose();

                    rgbBuffer.flush();
                }
                else {
                    ImageIO.write(img, fmt.getFileExt(), bos); /* Write to byte array stream - prevent bogus I/O errors */
                }
            } catch (IOException iox) {
                Log.info("Error encoding image - " + iox.getMessage());
                return null;
            }
        }
        return bos;
    }
    
    private static final int MAX_WRITE_RETRIES = 6;
    
    private static LinkedList<BufferOutputStream> baoslist = new LinkedList<BufferOutputStream>();
    private static Object baos_lock = new Object();
    /**
     * Wrapper for IOImage.write - implements retries for busy files
     * @param img - buffered image to write
     * @param fmt - format to use for file
     * @param fname - filename
     * @throws IOException if error writing file
     */
    public static void imageIOWrite(BufferedImage img, ImageFormat fmt, File fname) throws IOException {
        int retrycnt = 0;
        boolean done = false;
        byte[] rslt;
        int rsltlen;
        BufferOutputStream baos;
        synchronized(baos_lock) {
            if(baoslist.isEmpty()) {
                baos = new BufferOutputStream();
            }
            else {
                baos = baoslist.removeFirst();
                baos.reset();
            }
        }
        synchronized(imageioLock) {
            ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
            if(fmt.getFileExt().equals("jpg")) {
                WritableRaster raster = img.getRaster();
                WritableRaster newRaster = raster.createWritableChild(0, 0, img.getWidth(),
                        img.getHeight(), 0, 0, new int[] {0, 1, 2});
                DirectColorModel cm = (DirectColorModel)img.getColorModel();
                DirectColorModel newCM = new DirectColorModel(cm.getPixelSize(),
                        cm.getRedMask(), cm.getGreenMask(), cm.getBlueMask());
                // now create the new buffer that is used ot write the image:
                BufferedImage rgbBuffer = new BufferedImage(newCM, newRaster, false, null);

                // Find a jpeg writer
                ImageWriter writer = null;
                Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpg");
                if (iter.hasNext()) {
                    writer = iter.next();
                }
                if(writer == null) {
                    Log.severe("No JPEG ENCODER - Java VM does not support JPEG encoding");
                    return;
                }
                ImageWriteParam iwp = writer.getDefaultWriteParam();
                iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(fmt.getQuality());

                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                writer.setOutput(ios);

                writer.write(null, new IIOImage(rgbBuffer, null, null), iwp);
                writer.dispose();

                rgbBuffer.flush();
            }
            else {
                ImageIO.write(img, fmt.getFileExt(), baos); /* Write to byte array stream - prevent bogus I/O errors */
            }
        }
        // Get buffer and length
        rslt = baos.buf;
        rsltlen = baos.len;
        
        File fcur = new File(fname.getPath());
        File fnew = new File(fname.getPath() + ".new");
        File fold = new File(fname.getPath() + ".old");
        while(!done) {
            RandomAccessFile f = null;
            try {
                f = new RandomAccessFile(fnew, "rw");
                f.write(rslt, 0, rsltlen);
                done = true;
            } catch (IOException fnfx) {
                if(retrycnt < MAX_WRITE_RETRIES) {
                    Debug.debug("Image file " + fname.getPath() + " - unable to write - retry #" + retrycnt);
                    try { Thread.sleep(50 << retrycnt); } catch (InterruptedException ix) { throw fnfx; }
                    retrycnt++;
                }
                else {
                    Log.info("Image file " + fname.getPath() + " - unable to write - failed");
                    throw fnfx;
                }
            } finally {
                if(f != null) {
                    try { f.close(); } catch (IOException iox) { done = false; }
                }
                if(done) {
                    if (preUpdateCommand != null && !preUpdateCommand.isEmpty()) {
                        try {
                            new ProcessBuilder(preUpdateCommand, fnew.getAbsolutePath()).start().waitFor();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    fcur.renameTo(fold);
                    fnew.renameTo(fname);
                    fold.delete();
                    if (postUpdateCommand != null && !postUpdateCommand.isEmpty()) {
                        try {
                            new ProcessBuilder(postUpdateCommand, fname.getAbsolutePath()).start().waitFor();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        // Put back in pool
        synchronized(baos_lock) {
            baoslist.addFirst(baos);
        }
    }
    /**
     * Wrapper for IOImage.read - implements retries for busy files
     * @param fname - file to read
     * @return buffered image with contents
     * @throws IOException if error reading file
     */
    public static BufferedImage imageIORead(File fname) throws IOException {
        int retrycnt = 0;
        boolean done = false;
        BufferedImage img = null;
        
        while(!done) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(fname);
                byte[] b = new byte[(int) fname.length()];
                fis.read(b);
                fis.close();
                fis = null;
                BufferInputStream bais = new BufferInputStream(b);
                synchronized(imageioLock) {
                    ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
                    img = ImageIO.read(bais);
                }
                bais.close();
                done = true;    /* Done if no I/O error - retries don't fix format errors */
            } catch (IOException iox) {
            } finally {
                if(fis != null) {
                    try { fis.close(); } catch (IOException io) {}
                    fis = null;
                }
            }
            if(!done) {
                if(retrycnt < MAX_WRITE_RETRIES) {
                    Debug.debug("Image file " + fname.getPath() + " - unable to write - retry #" + retrycnt);
                    try { Thread.sleep(50 << retrycnt); } catch (InterruptedException ix) { }
                    retrycnt++;
                }
                else {
                    Log.info("Image file " + fname.getPath() + " - unable to read - failed");
                    throw new IOException("Error reading image file " + fname.getPath());
                }
            }
        }
        return img;
    }
    
    public static BufferedImage imageIODecode(InputStream str) throws IOException {
        synchronized(imageioLock) {
            ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
            return ImageIO.read(str);
        }
    }
}
