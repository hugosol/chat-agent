package com.hugosol.chatagent.model;

import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fsrs_parameters")
public class FsrsParameters extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private double w0;
    @Column(nullable = false)
    private double w1;
    @Column(nullable = false)
    private double w2;
    @Column(nullable = false)
    private double w3;
    @Column(nullable = false)
    private double w4;
    @Column(nullable = false)
    private double w5;
    @Column(nullable = false)
    private double w6;
    @Column(nullable = false)
    private double w7;
    @Column(nullable = false)
    private double w8;
    @Column(nullable = false)
    private double w9;
    @Column(nullable = false)
    private double w10;
    @Column(nullable = false)
    private double w11;
    @Column(nullable = false)
    private double w12;
    @Column(nullable = false)
    private double w13;
    @Column(nullable = false)
    private double w14;
    @Column(nullable = false)
    private double w15;
    @Column(nullable = false)
    private double w16;
    @Column(nullable = false)
    private double w17;
    @Column(nullable = false)
    private double w18;
    @Column(nullable = false)
    private double w19;
    @Column(nullable = false)
    private double w20;

    @Column(nullable = false)
    private boolean enableShortTerm = true;

    public FsrsParameters() {
    }

    public static FsrsParameters defaults(String userId) {
        double[] defaultWeights = FsrsSchedulerConfig.defaults().weights();
        FsrsParameters p = new FsrsParameters();
        p.userId = userId;
        p.w0 = defaultWeights[0];
        p.w1 = defaultWeights[1];
        p.w2 = defaultWeights[2];
        p.w3 = defaultWeights[3];
        p.w4 = defaultWeights[4];
        p.w5 = defaultWeights[5];
        p.w6 = defaultWeights[6];
        p.w7 = defaultWeights[7];
        p.w8 = defaultWeights[8];
        p.w9 = defaultWeights[9];
        p.w10 = defaultWeights[10];
        p.w11 = defaultWeights[11];
        p.w12 = defaultWeights[12];
        p.w13 = defaultWeights[13];
        p.w14 = defaultWeights[14];
        p.w15 = defaultWeights[15];
        p.w16 = defaultWeights[16];
        p.w17 = defaultWeights[17];
        p.w18 = defaultWeights[18];
        p.w19 = defaultWeights[19];
        p.w20 = defaultWeights[20];
        return p;
    }

    public double[] getWeights() {
        return new double[]{w0, w1, w2, w3, w4, w5, w6, w7, w8, w9,
                w10, w11, w12, w13, w14, w15, w16, w17, w18, w19, w20};
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public double getW0() { return w0; }
    public void setW0(double w0) { this.w0 = w0; }
    public double getW1() { return w1; }
    public void setW1(double w1) { this.w1 = w1; }
    public double getW2() { return w2; }
    public void setW2(double w2) { this.w2 = w2; }
    public double getW3() { return w3; }
    public void setW3(double w3) { this.w3 = w3; }
    public double getW4() { return w4; }
    public void setW4(double w4) { this.w4 = w4; }
    public double getW5() { return w5; }
    public void setW5(double w5) { this.w5 = w5; }
    public double getW6() { return w6; }
    public void setW6(double w6) { this.w6 = w6; }
    public double getW7() { return w7; }
    public void setW7(double w7) { this.w7 = w7; }
    public double getW8() { return w8; }
    public void setW8(double w8) { this.w8 = w8; }
    public double getW9() { return w9; }
    public void setW9(double w9) { this.w9 = w9; }
    public double getW10() { return w10; }
    public void setW10(double w10) { this.w10 = w10; }
    public double getW11() { return w11; }
    public void setW11(double w11) { this.w11 = w11; }
    public double getW12() { return w12; }
    public void setW12(double w12) { this.w12 = w12; }
    public double getW13() { return w13; }
    public void setW13(double w13) { this.w13 = w13; }
    public double getW14() { return w14; }
    public void setW14(double w14) { this.w14 = w14; }
    public double getW15() { return w15; }
    public void setW15(double w15) { this.w15 = w15; }
    public double getW16() { return w16; }
    public void setW16(double w16) { this.w16 = w16; }
    public double getW17() { return w17; }
    public void setW17(double w17) { this.w17 = w17; }
    public double getW18() { return w18; }
    public void setW18(double w18) { this.w18 = w18; }
    public double getW19() { return w19; }
    public void setW19(double w19) { this.w19 = w19; }
    public double getW20() { return w20; }
    public void setW20(double w20) { this.w20 = w20; }
    public void setWeights(double[] weights) {
        this.w0 = weights[0];
        this.w1 = weights[1];
        this.w2 = weights[2];
        this.w3 = weights[3];
        this.w4 = weights[4];
        this.w5 = weights[5];
        this.w6 = weights[6];
        this.w7 = weights[7];
        this.w8 = weights[8];
        this.w9 = weights[9];
        this.w10 = weights[10];
        this.w11 = weights[11];
        this.w12 = weights[12];
        this.w13 = weights[13];
        this.w14 = weights[14];
        this.w15 = weights[15];
        this.w16 = weights[16];
        this.w17 = weights[17];
        this.w18 = weights[18];
        this.w19 = weights[19];
        this.w20 = weights[20];
    }

    public boolean isEnableShortTerm() { return enableShortTerm; }
    public void setEnableShortTerm(boolean enableShortTerm) { this.enableShortTerm = enableShortTerm; }
}
