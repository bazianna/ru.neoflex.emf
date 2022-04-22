package ru.neoflex.emf.timezonedb;

public class TimeShift {
    private String gmtDT;
    private String timeZone;
    private String abbreviation;
    private int gmtOffset;
    private String localDT;

    public String getGmtDT() {return gmtDT;}

    public void setGmtDT(String gmtDT) {
        this.gmtDT = gmtDT;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getLocalDT() {
        return localDT;
    }

    public void setLocalDT(String localDT) {
        this.localDT = localDT;
    }

    public int getGmtOffset() {
        return gmtOffset;
    }

    public void setGmtOffset(int gmtOffset) {
        this.gmtOffset = gmtOffset;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }
}
