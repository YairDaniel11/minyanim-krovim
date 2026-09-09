package com.meir.minyanimkrovim;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * חישוב קירוב (דיוק של כדקה-שתיים) לזמני היום ההלכתיים, לפי הנוסחה
 * האסטרונומית הקלאסית מתוך "Almanac for Computers" (1990) לחישוב נץ/שקיעה
 * בזוויות שונות מתחת לאופק, ועליהן בנויים שאר הזמנים לפי שיטת "שעה זמנית"
 * (חלוקת האור/החושך ל-12 חלקים שווים) - השיטה הנפוצה והמוכרת ביותר
 * (הגר"א ורוב הלוחות).
 *
 * הערה חשובה: זהו חישוב אסטרונומי כללי - לא תחליף ללוח זמנים הלכתי מוסמך.
 * יש מנהגים שונים לגבי הזוויות המדויקות (למשל צאת הכוכבים/עלות השחר לפי
 * שיטות שונות) - כאן נבחרו ערכים נפוצים ומקובלים לשימוש כללי. לפסיקה
 * הלכתית מדויקת (חתונה, תענית, ספירת העומר וכו') יש להיוועץ ברב או בלוח
 * זמנים הלכתי מוסמך.
 */
public class SunTimes {

    public static class Result {
        public final boolean valid;
        public final int sunriseMinutes; // דקות-מחצות, שעון מקומי
        public final int sunsetMinutes;

        Result(boolean valid, int sunriseMinutes, int sunsetMinutes) {
            this.valid = valid;
            this.sunriseMinutes = sunriseMinutes;
            this.sunsetMinutes = sunsetMinutes;
        }
    }

    /** זמני היום המלאים - כולם בדקות-מחצות, שעון מקומי. -1 = לא ניתן לחישוב. */
    public static class Zmanim {
        public boolean valid;
        public int alotHashachar;      // עלות השחר (16.1 מעלות)
        public int netzHachama;        // הנץ החמה
        public int sofZmanShemaGra;    // סוף זמן קריאת שמע (גר"א, 3 שעות זמניות)
        public int sofZmanTefilaGra;   // סוף זמן תפילה (גר"א, 4 שעות זמניות)
        public int chatzotHayom;       // חצות היום
        public int minchaGedola;       // מנחה גדולה
        public int minchaKetana;       // מנחה קטנה
        public int plagHamincha;       // פלג המנחה
        public int shkiatHachama;      // שקיעת החמה
        public int tzeitHakochavim;    // צאת הכוכבים (8.5 מעלות)
    }

    private static final double ZEN_STANDARD = 90.833; // נץ/שקיעה רגילים (כולל שבירה + רדיוס גלגל החמה)
    private static final double ZEN_ALOT = 106.1;       // עלות השחר - 16.1 מעלות מתחת לאופק
    private static final double ZEN_TZEIT = 98.5;       // צאת הכוכבים - 8.5 מעלות מתחת לאופק

    /** מחשב נץ ושקיעה (רגילים) לתאריך של ה-Calendar הנתון. */
    public static Result compute(double latitude, double longitude, Calendar date) {
        int sunrise = computeMinutesForZenith(latitude, longitude, date, ZEN_STANDARD, true);
        int sunset = computeMinutesForZenith(latitude, longitude, date, ZEN_STANDARD, false);
        if (sunrise < 0 || sunset < 0) return new Result(false, -1, -1);
        return new Result(true, sunrise, sunset);
    }

    /** מחשב את כל זמני היום ההלכתיים העיקריים לתאריך הנתון. */
    public static Zmanim computeZmanim(double latitude, double longitude, Calendar date) {
        Zmanim z = new Zmanim();
        int netz = computeMinutesForZenith(latitude, longitude, date, ZEN_STANDARD, true);
        int shkia = computeMinutesForZenith(latitude, longitude, date, ZEN_STANDARD, false);
        int alot = computeMinutesForZenith(latitude, longitude, date, ZEN_ALOT, true);
        int tzeit = computeMinutesForZenith(latitude, longitude, date, ZEN_TZEIT, false);

        if (netz < 0 || shkia < 0) {
            z.valid = false;
            return z;
        }

        double shaahZmanit = (shkia - netz) / 12.0; // "שעה זמנית" בדקות (שיטת הגר"א - היום מנץ עד שקיעה)

        z.valid = true;
        z.alotHashachar = alot >= 0 ? alot : round(netz - 90); // נפילה חלקה: 90 דק' לפני נץ אם הזווית לא ניתנת לחישוב
        z.netzHachama = netz;
        z.sofZmanShemaGra = round(netz + 3 * shaahZmanit);
        z.sofZmanTefilaGra = round(netz + 4 * shaahZmanit);
        z.chatzotHayom = round(netz + 6 * shaahZmanit);
        z.minchaGedola = round(netz + 6.5 * shaahZmanit);
        z.minchaKetana = round(netz + 9.5 * shaahZmanit);
        z.plagHamincha = round(netz + 10.75 * shaahZmanit);
        z.shkiatHachama = shkia;
        z.tzeitHakochavim = tzeit >= 0 ? tzeit : round(shkia + 72); // נפילה חלקה: 72 דק' אחרי שקיעה

        return z;
    }

    private static int round(double minutes) {
        int m = (int) Math.round(minutes);
        return ((m % 1440) + 1440) % 1440;
    }

    private static int computeMinutesForZenith(double latitude, double longitude, Calendar date,
                                                double zenith, boolean sunrise) {
        int dayOfYear = date.get(Calendar.DAY_OF_YEAR);
        TimeZone tz = date.getTimeZone();
        long millis = date.getTimeInMillis();
        double utcOffsetHours = tz.getOffset(millis) / 3600000.0;

        double utc = calcUtcHours(dayOfYear, latitude, longitude, sunrise, zenith);
        if (Double.isNaN(utc)) return -1;
        return toLocalMinutes(utc, utcOffsetHours);
    }

    private static int toLocalMinutes(double utcHours, double utcOffsetHours) {
        double local = utcHours + utcOffsetHours;
        local = ((local % 24) + 24) % 24;
        int totalMinutes = (int) Math.round(local * 60);
        return totalMinutes % 1440;
    }

    /** מחזיר NaN אם השמש לא מגיעה לזווית הזו באותו יום באותו קו רוחב (לילה/יום קוטבי). */
    private static double calcUtcHours(int dayOfYear, double lat, double lon, boolean sunrise, double zenith) {
        double lngHour = lon / 15.0;
        double t = dayOfYear + (((sunrise ? 6 : 18) - lngHour) / 24.0);

        double M = (0.9856 * t) - 3.289;

        double L = M + (1.916 * sinDeg(M)) + (0.020 * sinDeg(2 * M)) + 282.634;
        L = normalize(L, 360);

        double RA = atanDeg(0.91764 * tanDeg(L));
        RA = normalize(RA, 360);

        double Lquadrant = Math.floor(L / 90.0) * 90.0;
        double RAquadrant = Math.floor(RA / 90.0) * 90.0;
        RA = RA + (Lquadrant - RAquadrant);
        RA = RA / 15.0;

        double sinDec = 0.39782 * sinDeg(L);
        double cosDec = cosDeg(asinDeg(sinDec));

        double cosH = (cosDeg(zenith) - (sinDec * sinDeg(lat))) / (cosDec * cosDeg(lat));
        if (cosH > 1.0 || cosH < -1.0) {
            return Double.NaN;
        }

        double H = sunrise ? (360.0 - acosDeg(cosH)) : acosDeg(cosH);
        H = H / 15.0;

        double T = H + RA - (0.06571 * t) - 6.622;
        double UT = T - lngHour;
        UT = ((UT % 24) + 24) % 24;
        return UT;
    }

    private static double normalize(double value, double mod) {
        double v = value % mod;
        return v < 0 ? v + mod : v;
    }

    private static double sinDeg(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cosDeg(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double tanDeg(double deg) { return Math.tan(Math.toRadians(deg)); }
    private static double asinDeg(double v) { return Math.toDegrees(Math.asin(v)); }
    private static double acosDeg(double v) { return Math.toDegrees(Math.acos(v)); }
    private static double atanDeg(double v) { return Math.toDegrees(Math.atan(v)); }
}
