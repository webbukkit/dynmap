package org.dynmap.utils;

import org.json.simple.JSONArray;

/**
 * Basic 3D matrix math class - prevent dependency on Java 3D for this
 */
public class Matrix3D {
    private double m11, m12, m13, m21, m22, m23, m31, m32, m33;
    /**
     * Construct identity matrix
     */
    public Matrix3D() {
        m11 = m22 = m33 = 1.0;
        m12 = m13 = m21 = m23 = m31 = m32 = 0.0;
    }
    /**
     * Construct matrix with given parms
     * 
     * @param m11 - first cell of first row
     * @param m12 - second cell of first row
     * @param m13 - third cell of first row
     * @param m21 - first cell of second row
     * @param m22 - second cell of second row
     * @param m23 - third cell of second row
     * @param m31 - first cell of third row
     * @param m32 - second cell of third row
     * @param m33 - third cell of third row
     */
    public Matrix3D(double m11, double m12, double m13, double m21, double m22, double m23, double m31, double m32, double m33) {
        this.m11 = m11; this.m12 = m12; this.m13 = m13;
        this.m21 = m21; this.m22 = m22; this.m23 = m23;
        this.m31 = m31; this.m32 = m32; this.m33 = m33;
    }
    /**
     * Multiply matrix by another matrix (this = mat * this), and store result in self
     * @param mat - matrix to multiply
     */
    public void multiply(Matrix3D mat) {
        double new_m11 = mat.m11*m11 + mat.m12*m21 + mat.m13*m31;
        double new_m12 = mat.m11*m12 + mat.m12*m22 + mat.m13*m32;
        double new_m13 = mat.m11*m13 + mat.m12*m23 + mat.m13*m33;
        double new_m21 = mat.m21*m11 + mat.m22*m21 + mat.m23*m31;
        double new_m22 = mat.m21*m12 + mat.m22*m22 + mat.m23*m32;
        double new_m23 = mat.m21*m13 + mat.m22*m23 + mat.m23*m33;
        double new_m31 = mat.m31*m11 + mat.m32*m21 + mat.m33*m31;
        double new_m32 = mat.m31*m12 + mat.m32*m22 + mat.m33*m32;
        double new_m33 = mat.m31*m13 + mat.m32*m23 + mat.m33*m33;
        m11 = new_m11; m12 = new_m12; m13 = new_m13;
        m21 = new_m21; m22 = new_m22; m23 = new_m23;
        m31 = new_m31; m32 = new_m32; m33 = new_m33;
    }
    /** 
     * Scale each coordinate by given values
     * 
     * @param s1 - X scale
     * @param s2 - Y scale
     * @param s3 - Z scale
     */
    public void scale(double s1, double s2, double s3) {
        Matrix3D scalemat = new Matrix3D(s1, 0, 0, 0, s2, 0, 0, 0, s3);
        multiply(scalemat);
    }
    /**
     * Rotate XY clockwise around +Z axis
     * @param rot_deg - degrees of rotation
     */
    public void rotateXY(double rot_deg) {
        double rot_rad = Math.toRadians(rot_deg);
        double sin_rot = Math.sin(rot_rad);
        double cos_rot = Math.cos(rot_rad);
        Matrix3D rotmat = new Matrix3D(cos_rot, sin_rot, 0, -sin_rot, cos_rot, 0, 0, 0, 1);
        multiply(rotmat);
    }
    /**
     * Rotate XZ clockwise around +Y axis
     * @param rot_deg - degrees of rotation
     */
    public void rotateXZ(double rot_deg) {
        double rot_rad = Math.toRadians(rot_deg);
        double sin_rot = Math.sin(rot_rad);
        double cos_rot = Math.cos(rot_rad);
        Matrix3D rotmat = new Matrix3D(cos_rot, 0, -sin_rot, 0, 1, 0, sin_rot, 0, cos_rot);
        multiply(rotmat);
    }
    /**
     * Rotate YZ clockwise around +X axis
     * @param rot_deg - degrees of rotation
     */
    public void rotateYZ(double rot_deg) {
        double rot_rad = Math.toRadians(rot_deg);
        double sin_rot = Math.sin(rot_rad);
        double cos_rot = Math.cos(rot_rad);
        Matrix3D rotmat = new Matrix3D(1, 0, 0, 0, cos_rot, sin_rot, 0, -sin_rot, cos_rot);
        multiply(rotmat);
    }
    /**
     * Shear along Z axis by factor of X and Y
     * @param x_fact - X shear
     * @param y_fact - Y shear
     */
    public void shearZ(double x_fact, double y_fact) {
        Matrix3D shearmat = new Matrix3D(1, 0, 0, 0, 1, 0, x_fact, y_fact, 1);
        multiply(shearmat);        
    }
    /**
     * Transform a given vector using the matrix
     * @param v - array[3] of vector coords (input, updated for output)
     */
    public final void transform(double[] v) {
        double v1 = m11*v[0] + m12*v[1] + m13*v[2];
        double v2 = m21*v[0] + m22*v[1] + m23*v[2];
        double v3 = m31*v[0] + m32*v[1] + m33*v[2];
        v[0] = v1; v[1] = v2; v[2] = v3;
    }
    /**
     * Transform a given vector using the matrix
     * @param v - vector input (updated for output)
     */
    public final void transform(Vector3D v) {
        double v1 = m11*v.x + m12*v.y + m13*v.z;
        double v2 = m21*v.x + m22*v.y + m23*v.z;
        double v3 = m31*v.x + m32*v.y + m33*v.z;
        v.x = v1; v.y = v2; v.z = v3;
    }

    /**
     * Transform a given vector using the matrix - put result in provided output vector
     * @param v - input vector
     * @param outv - output vector
     */
    public final void transform(Vector3D v, Vector3D outv) {
        outv.x = m11*v.x + m12*v.y + m13*v.z;
        outv.y = m21*v.x + m22*v.y + m23*v.z;
        outv.z = m31*v.x + m32*v.y + m33*v.z;
    }

    public String toString() {
        return "[ [" + m11 + " " + m12 + " " + m13 + "] [" + m21 + " " + m22 + " " + m23 + "] [" + m31 + " " + m32 + " " + m33 + "] ]";
    }
    
    @SuppressWarnings("unchecked")
    public JSONArray toJSON() {
        JSONArray array = new JSONArray();
        array.add(m11);
        array.add(m12);
        array.add(m13);
        array.add(m21);
        array.add(m22);
        array.add(m23);
        array.add(m31);
        array.add(m32);
        array.add(m33);
        return array;
    }
}
