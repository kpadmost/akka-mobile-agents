package com.kpadmost.board;

import com.kpadmost.boardactors.WorkerAgent;
import com.kpadmost.serialization.CborSerializable;


public class BoardS implements  CborSerializable {
    // TODO: this is stub for a physics-oriended board

    // this is state



    private int x = 25;
    private int y = 25;
    int counter = 0;
    private Speed speed = new Speed(10, 18);


    public final int XMIN = 0;
    public final int XMAX = 1750;
    public final int yMIN = 0;
    public final int YMAX = 2765;

    public final int WIDTH = 30;
    public final int HEIGHT = 30;

    public BoardS() {}

    public BoardS(BoardS board) {
        this.x = board.x;
        this.y = board.y;
        this.speed = board.speed;
        this.counter = board.counter;
    }


    public static final class Speed implements CborSerializable {
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


    public final void update() {
        x = newX();
        y = newY();
        ++counter;
    }

    @Override
    public String toString() {
        try {
            if(counter % 50 == 0)
                System.out.println("c " + counter);
           return String.format("%d:%d:%d", x, y, counter);
        } catch (Exception e) {
            System.out.println("exception in update:" + e.getMessage());
        }
        return "-1:-1";
    }


}
