package org.dynmap.utils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;
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
import org.dynmap.MapType.ImageEncoding;
import org.dynmap.MapType.ImageFormat;
import org.dynmap.storage.MapStorageTile;

import com.google.common.io.Files;

import org.dynmap.DynmapCore;

/**
 * Implements soft-locks for prevent concurrency issues with file updates
 */
public class ImageIOManager {
    public static String preUpdateCommand = null;
    public static String postUpdateCommand = null;
    private static Object imageioLock = new Object();
    public static DynmapCore core;	// Injected during enableCore
    
    private static boolean did_warning = false;
    
    private static ImageFormat validateFormat(ImageFormat fmt) {
    	// If WEBP, see if supported
    	if (fmt.getEncoding() == ImageEncoding.WEBP) {
    		if (core.getCWEBPPath() == null) {	// No encoder?
    			if (!did_warning) {
    				Log.warning("Attempt to use WEBP support when not usable: using JPEG");
    				did_warning = true;
    			}
				fmt = ImageFormat.FORMAT_JPG;	// Switch to JPEN
    		}
    	}
    	return fmt;
    }
    
    private static void doWEBPEncode(BufferedImage img, ImageFormat fmt, OutputStream out) throws IOException {
        BufferOutputStream bos = new BufferOutputStream();
        
        ImageIO.write(img, "png", bos); // Encode as PNG in buffere output stream
        // Write to a tmp file
        File tmpfile = File.createTempFile("pngToWebp", "png");
        FileOutputStream fos = new FileOutputStream(tmpfile);
        fos.write(bos.buf, 0, bos.len);
        fos.close();
        // Run encoder to new new temp file
        File tmpfile2 = File.createTempFile("pngToWebp", "webp");
        String args[] = { core.getCWEBPPath(), "-q", Integer.toString((int)fmt.getQuality()), tmpfile.getAbsolutePath(), "-o", tmpfile2.getAbsolutePath() };
        Process pr = Runtime.getRuntime().exec(args);
        try {
        	pr.waitFor();
        } catch (InterruptedException ix) {
        	throw new IOException("Error waiting for encoder");
        }
        // Read output file into output stream
        Files.copy(tmpfile2, out);
        out.flush();
        // Clean up temp files
        tmpfile.delete();
        tmpfile2.delete();
    }

    private static BufferedImage doWEBPDecode(BufferInputStream buf) throws IOException {
        // Write to a tmp file
        File tmpfile = File.createTempFile("webpToPng", "webp");
        Files.write(buf.buffer(), tmpfile);
        // Run encoder to new new temp file
        File tmpfile2 = File.createTempFile("webpToPng", "png");
        String args[] = { core.getDWEBPPath(), tmpfile.getAbsolutePath(), "-o", tmpfile2.getAbsolutePath() };
        Process pr = Runtime.getRuntime().exec(args);
        try {
        	pr.waitFor();
        } catch (InterruptedException ix) {
        	throw new IOException("Error waiting for encoder");
        }
        // Read file
        BufferedImage obuf = ImageIO.read(tmpfile2);
        // Clean up temp files
        tmpfile.delete();
        tmpfile2.delete();
        
        return obuf;
    }

    public static BufferOutputStream imageIOEncode(BufferedImage img, ImageFormat fmt) {
        BufferOutputStream bos = new BufferOutputStream();

        synchronized(imageioLock) {
            try {
                ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
                
                fmt = validateFormat(fmt);
                
                if(fmt.getEncoding() == ImageEncoding.JPG) {
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
                else if (fmt.getEncoding() == ImageEncoding.WEBP) {
                	doWEBPEncode(img, fmt, bos);                	
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
    
    public static BufferedImage imageIODecode(MapStorageTile.TileRead tr) throws IOException {
        synchronized(imageioLock) {
            ImageIO.setUseCache(false); /* Don't use file cache - too small to be worth it */
            if (tr.format == ImageEncoding.WEBP) {
                return doWEBPDecode(tr.image);
            }
            return ImageIO.read(tr.image);
        }
    }
}
