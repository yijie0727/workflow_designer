package test;

import java.io.Serializable;

public class Cat implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String color;
    private int size;

    public Cat(String name, String color, int size) {
        this.name = name;
        this.color = color;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
