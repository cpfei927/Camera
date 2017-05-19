package com.cpfei.camera;

import java.io.Serializable;

/**
 * Created by cpfei on 2017/5/16.
 */

public class Point implements Serializable {

    private Integer x;
    private Integer y;
    private Integer z;

    public int getX() {
        return x == null ? 0 : x.intValue();
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y == null ? 0 : y.intValue();
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z == null ? 0 : z.intValue();
    }

    public void setZ(int z) {
        this.z = z;
    }


}
