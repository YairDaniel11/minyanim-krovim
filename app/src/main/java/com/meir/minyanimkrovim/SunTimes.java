package com.meir.minyanimkrovim;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * חישוב זמני היום ההלכתיים לפי האלגוריתם האסטרונומי של NOAA (מבוסס Meeus,
 * "Astronomical Algorithms") - דיוק של שניות בודדות לנץ/שקיעה.
 * נבדק מול Hebcal (16 צירופים של 4 מקומות ו-4 תאריכים): הפרש מקסימלי דקה אחת
 * בחצות, ובשאר הזמנים אפס.
 *
 * "שעה זמנית" (גר"א): היום מנץ עד שקיעה, מחולק ל-12.
 * "שעה זמנית" (מג"א): היום מעלות השחר (72 דקות לפני הנץ) עד צאת הכוכבים
 * (72 דקות אחרי השקיעה - גם הוא צאת ר"ת), מחולק ל-12.
 *
 * הערה: חישוב אסטרונומי כללי (גובה פני הים) - לא תחליף ללוח זמנים הלכתי מוסמך.
 * מנהגים שונים לגבי הזוויות המדויקות (עלות השחר 16.1, צאת הכוכבים 8.5) - כאן
 * נבחרו הערכים הנפוצים.
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

    /** זמני היום המלאים - כולם בדקות-מחצות, שעון מקומי. */
    public static class Zmanim {
        public boolean valid;
        public int alotHashachar;      // עלות השחר (16.1 מעלות)
        public int alot72;             // עלות השחר - 72 דקות לפני הנץ (מג"א)
        public int netzHachama;        // הנץ החמה
        public int sofZmanShemaMga;    // סוף זמן קריאת שמע - מג"א (72 דק')
        public int sofZmanShemaGra;    // סוף זמן קריאת שמע - גר"א
        public int sofZmanTefilaMga;   // סוף זמן תפילה - מג"א (72 דק')
        public int sofZmanTefilaGra;   // סוף זמן תפילה - גר"א
        public int chatzotHayom;       // חצות היום
        public int minchaGedola;       // מנחה גדולה (גר"א)
        public int minchaKetana;       // מנחה קטנה (גר"א)
        public int plagHamincha;       // פלג המנחה (גר"א)
        public int shkiatHachama;      // שקיעת החמה
        public int tzeitHakochavim;    // צאת הכוכבים (8.5 מעלות)
        public int tzeitRt72;          // צאת הכוכבים לרבנו תם (72 דקות אחרי השקיעה)
    }

    private static final double ZEN_STANDARD = 90.833; // נץ/שקיעה (שבירה + רדיוס גלגל החמה)
    private static final double ZEN_ALOT = 106.1;       // 16.1 מעלות מתחת לאופק
    private static final double ZEN_TZEIT = 98.5;       // 8.5 מעלות מתחת לאופק

    /** מחשב נץ ושקיעה (רגילים) לתאריך של ה-Calendar הנתון. */
    public static Result compute(double latitude, double longitude, Calendar date) {
        double off = offsetMinutes(date);
        double sunrise = eventLocalMinutes(date, latitude, longitude, ZEN_STANDARD, true, off);
        double sunset = eventLocalMinutes(date, latitude, longitude, ZEN_STANDARD, false, off);
        if (Double.isNaN(sunrise) || Double.isNaN(sunset)) return new Result(false, -1, -1);
        return new Result(true, round(sunrise), round(sunset));
    }

    /** מחשב את כל זמני היום ההלכתיים העיקריים לתאריך הנתון. */
    public static Zmanim computeZmanim(double latitude, double longitude, Calendar date) {
        Zmanim z = new Zmanim();
        double off = offsetMinutes(date);
        double netz = eventLocalMinutes(date, latitude, longitude, ZEN_STANDARD, true, off);
        double shkia = eventLocalMinutes(date, latitude, longitude, ZEN_STANDARD, false, off);
        if (Double.isNaN(netz) || Double.isNaN(shkia)) {
            z.valid = false;
            return z;
        }
        double alot = eventLocalMinutes(date, latitude, longitude, ZEN_ALOT, true, off);
        double tzeit = eventLocalMinutes(date, latitude, longitude, ZEN_TZEIT, false, off);

        double shaaGra = (shkia - netz) / 12.0;
        double alot72 = netz - 72;
        double tzeit72 = shkia + 72;
        double shaaMga = (tzeit72 - alot72) / 12.0;

        z.valid = true;
        z.alotHashachar = Double.isNaN(alot) ? round(netz - 90) : round(alot);
        z.alot72 = round(alot72);
        z.netzHachama = round(netz);
        z.sofZmanShemaMga = round(alot72 + 3 * shaaMga);
        z.sofZmanShemaGra = round(netz + 3 * shaaGra);
        z.sofZmanTefilaMga = round(alot72 + 4 * shaaMga);
        z.sofZmanTefilaGra = round(netz + 4 * shaaGra);
        z.chatzotHayom = round(netz + 6 * shaaGra);
        z.minchaGedola = round(netz + 6.5 * shaaGra);
        z.minchaKetana = round(netz + 9.5 * shaaGra);
        z.plagHamincha = round(netz + 10.75 * shaaGra);
        z.shkiatHachama = round(shkia);
        z.tzeitHakochavim = Double.isNaN(tzeit) ? round(shkia + 40) : round(tzeit);
        z.tzeitRt72 = round(tzeit72);
        return z;
    }

    private static int round(double minutes) {
        int m = (int) Math.round(minutes);
        return ((m % 1440) + 1440) % 1440;
    }

    /** היסט אזור הזמן (בדקות) בצהריים של התאריך הנתון - נכון גם בימי מעבר שעון קיץ. */
    private static double offsetMinutes(Calendar date) {
        Calendar noon = (Calendar) date.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        TimeZone tz = noon.getTimeZone();
        return tz.getOffset(noon.getTimeInMillis()) / 60000.0;
    }

    /** מספר ימי יוליאני ב-0h UT של התאריך האזרחי של ה-Calendar. */
    private static double julianDay(Calendar date) {
        int y = date.get(Calendar.YEAR);
        int mo = date.get(Calendar.MONTH) + 1;
        int d = date.get(Calendar.DAY_OF_MONTH);
        if (mo <= 2) {
            y -= 1;
            mo += 12;
        }
        int a = y / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (mo + 1)) + d + b - 1524.5;
    }

    /** מחזיר {equationOfTime (דקות), declination (מעלות)} ברגע T (מאות שנה יוליאניות מ-J2000). */
    private static double[] sunParams(double t) {
        double l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0;
        double m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double c = Math.sin(Math.toRadians(m)) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(Math.toRadians(2 * m)) * (0.019993 - 0.000101 * t)
                + Math.sin(Math.toRadians(3 * m)) * 0.000289;
        double trueLong = l0 + c;
        double omega = 125.04 - 1934.136 * t;
        double app = trueLong - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
        double seco = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0;
        double eps = seco + 0.00256 * Math.cos(Math.toRadians(omega));
        double dec = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(eps)) * Math.sin(Math.toRadians(app))));
        double y = Math.pow(Math.tan(Math.toRadians(eps / 2.0)), 2);
        double eqt = 4.0 * Math.toDegrees(
                y * Math.sin(2 * Math.toRadians(l0))
                        - 2 * e * Math.sin(Math.toRadians(m))
                        + 4 * e * y * Math.sin(Math.toRadians(m)) * Math.cos(2 * Math.toRadians(l0))
                        - 0.5 * y * y * Math.sin(4 * Math.toRadians(l0))
                        - 1.25 * e * e * Math.sin(2 * Math.toRadians(m)));
        return new double[]{eqt, dec};
    }

    /**
     * רגע (בדקות מחצות, שעון מקומי) שבו השמש מגיעה לזווית zenith בעלייה/שקיעה; NaN אם אין
     * (יום/לילה קוטבי). שלוש איטרציות מתכנסות לשניות.
     */
    private static double eventLocalMinutes(Calendar date, double lat, double lon, double zenith,
                                            boolean rising, double offsetMinutes) {
        double j0 = julianDay(date);
        double tMin = 720.0 - 4.0 * lon; // ניחוש ראשוני: צהריים סולרי (דקות UTC מ-0h)
        for (int i = 0; i < 3; i++) {
            double t = (j0 + tMin / 1440.0 - 2451545.0) / 36525.0;
            double[] p = sunParams(t);
            double eqt = p[0];
            double dec = Math.toRadians(p[1]);
            double latR = Math.toRadians(lat);
            double cosH = (Math.cos(Math.toRadians(zenith)) - Math.sin(latR) * Math.sin(dec))
                    / (Math.cos(latR) * Math.cos(dec));
            if (cosH > 1.0 || cosH < -1.0) return Double.NaN;
            double h = Math.toDegrees(Math.acos(cosH));
            double noon = 720.0 - 4.0 * lon - eqt;
            tMin = rising ? noon - 4.0 * h : noon + 4.0 * h;
        }
        double local = tMin + offsetMinutes;
        return ((local % 1440.0) + 1440.0) % 1440.0;
    }
}
