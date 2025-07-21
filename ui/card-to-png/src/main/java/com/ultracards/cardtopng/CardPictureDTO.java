package com.ultracards.cardtopng;

import java.awt.image.BufferedImage;

class CardPictureDTO {

    private BufferedImage face;
    private BufferedImage back;

    public CardPictureDTO(BufferedImage face, BufferedImage back) {
        this.face = face;
        this.back = back;
    }

    public BufferedImage getFace() {
        return face;
    }

    public void setFace(BufferedImage face) {
        this.face = face;
    }

    public BufferedImage getBack() {
        return back;
    }

    public void setBack(BufferedImage back) {
        this.back = back;
    }
}
