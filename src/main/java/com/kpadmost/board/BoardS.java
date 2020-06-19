package com.kpadmost.board;

import com.kpadmost.boardactors.WorkerAgent;


public class BoardS implements IBoard {
    // TODO: this is stub for a physics-oriended board

    // this is state



    public int x = 25;
    public int y = 25;
    public Speed speed = new Speed(10, 18);


    public final int XMIN = 0;
    public final int XMAX = 400;
    public final int yMIN = 0;
    public final int YMAX = 400;

    public final int WIDTH = 30;
    public final int HEIGHT = 30;

    public BoardS(int x, int y, int dx, int dy) {
        this.x = x;
        this.y = y;
        this.speed = new Speed(dx, dy);
    }

    public BoardS() {}

    public static final class Speed {
        public int dx;
        public int dy;

        Speed(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }


    private int newX() {
        final int nX = x + speed.dx;
        if(nX + WIDTH >= XMAX || nX <= XMIN) {
            speed.dx = -speed.dx;
            return x;
        }
        return nX;
    }

    private int newY() {
        final int nY = y + speed.dy;
        if(nY + HEIGHT >= YMAX || nY <= yMIN) {
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
           return String.format("%d:%d", x, y);
        } catch (Exception e) {
            System.out.println("exception in update:" + e.getMessage());
        }
        return "-1:-1";
    }


}
