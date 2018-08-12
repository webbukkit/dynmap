package org.dynmap.utils;

import java.util.ArrayList;

public class Polygon {
    public static class Point2D {
        public double x, y;
        public Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    
    private ArrayList<Point2D> v = new ArrayList<Point2D>();
    
    public void addVertex(Point2D p) {
        v.add(p);
    }
    
    public void addVertex(double x, double y) {
        v.add(new Point2D(x, y));
    }
    
    public int size() {
        return v.size();
    }
       
    public Point2D getVertex(int i) {
        return v.get(i);
    }
       
    // Sutherland-Hodgman polygon clipping:
    public Polygon clip(double xmin, double ymin, double xmax, double ymax) {
        ArrayList<Point2D> newpoly = new ArrayList<Point2D>(v); // Make copy
        ArrayList<Point2D> poly = new ArrayList<Point2D>();
        ArrayList<Point2D> wrkpoly;
        int n;
        Point2D a, b;
        boolean aIns, bIns; // whether A or B is on the same side as the rectangle
        
        // Clip against x == xmax:
        n = newpoly.size();
        if (n > 0) {
            // Local through line segments - clip 
            b = newpoly.get(n-1);
            for (int i=0; i<n; i++) {
                a = b; 
                b = newpoly.get(i);
                aIns = a.x <= xmax; 
                bIns = b.x <= xmax;
                if (aIns != bIns) {
                    poly.add(new Point2D(xmax, a.y + (b.y - a.y) * (xmax - a.x)/(b.x - a.x)));
                }
                if (bIns) {
                    poly.add(b);
                }
            }
            wrkpoly = newpoly;
            newpoly = poly;
            poly = wrkpoly;
            poly.clear();

            // Clip against x == xmin:
            n = newpoly.size();
            if (n > 0) { 
                // Local through line segments - clip 
                b = newpoly.get(n-1);
                for (int i=0; i<n; i++) {
                    a = b;
                    b = newpoly.get(i);
                    aIns = a.x >= xmin;
                    bIns = b.x >= xmin;
                    if (aIns != bIns) {
                        poly.add(new Point2D(xmin, a.y + (b.y - a.y) * (xmin - a.x)/(b.x - a.x)));
                    }
                    if (bIns) {
                        poly.add(b);
                    }
                }
                wrkpoly = newpoly;
                newpoly = poly; 
                poly = wrkpoly;
                poly.clear();

                // Clip against y == ymax:
                n = newpoly.size();
                if (n > 0) { 
                    // Local through line segments - clip 
                    b = newpoly.get(n-1);
                    for (int i=0; i<n; i++) {
                        a = b;
                        b = newpoly.get(i);
                        aIns = a.y <= ymax;
                        bIns = b.y <= ymax;
                        if (aIns != bIns) {
                            poly.add(new Point2D(a.x + (b.x - a.x) * (ymax - a.y)/(b.y - a.y), ymax));
                        }
                        if (bIns) {
                            poly.add(b);
                        }
                    }
                    wrkpoly = newpoly;
                    newpoly = poly; 
                    poly = wrkpoly;
                    poly.clear();

                    // Clip against y == ymin:
                    n = newpoly.size();
                    if (n > 0) { 
                        b = newpoly.get(n-1);
                        for (int i=0; i<n; i++) {
                            a = b; 
                            b = newpoly.get(i);
                            aIns = a.y >= ymin; 
                            bIns = b.y >= ymin;
                            if (aIns != bIns) {
                                poly.add(new Point2D(a.x + (b.x - a.x) * (ymin - a.y)/(b.y - a.y), ymin));
                            }
                            if (bIns) {
                                poly.add(b);
                            }
                        }
                        wrkpoly = newpoly;
                        newpoly = poly; 
                    }
                }
            }
        }
        Polygon rslt = null;
        if (newpoly.size() > 0) {
            rslt = new Polygon();
            rslt.v = newpoly;
        }
        return rslt;
    }
}
