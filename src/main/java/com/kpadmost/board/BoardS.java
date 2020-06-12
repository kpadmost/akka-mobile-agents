package com.kpadmost.board;

import org.json.JSONObject;

public class BoardS implements IBoard {
    // TODO: this is stub for a physics-oriended board
    private int x = 10;
    private int y = 10;
    private Speed speed = new Speed(10, 15);


    public final int XMIN = 0;
    public final int XMAX = 400;
    public final int yMIN = 0;
    public final int YMAX = 400;

    public final int WIDTH = 30;
    public final int HEIGHT = 30;
    public BoardS() {}

    private static final class Speed {
        int dx;
        int dy;

        public Speed(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }


    private int newX() {
        final int nX = x + speed.dx;
        if(nX + WIDTH >= XMAX || nX <= 0) {
            speed.dx = -speed.dx;
            return x;
        }
        return nX;
    }

    private int newY() {
        final int nY = y + speed.dy;
        if(nY + HEIGHT >= YMAX || nY <= 0) {
            speed.dy = -speed.dy;
            return y;
        }
        return nY;
    }

    @Override
    public final void update() {
        x = newX();
        y = newY();
    }






    @Override
    public String toString() {
        try {
            JSONObject js = new JSONObject();
            js.put("x", x);
            js.put("y", y);
            return js.toString();
        } catch (Exception e) {
            System.out.println("exception in update:" + e.getMessage());
        }
        return "{'x': -1, 'y': -1}";
    }
}
