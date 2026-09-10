package com.meir.minyanimkrovim;

/**
 * רשימת ערים/שכונות מוכרות עם קואורדינטות מובנות מראש, לבחירה מהירה
 * במסך "זמני היום" - כדי שמשתמש לא יצטרך לחפש קואורדינטות ב-Google Maps
 * בעצמו. הקואורדינטות הן ברמת דיוק עירונית כללית (לא נקודה הלכתית
 * מדויקת) - מספיקות לחישוב שקיעה/נץ שממילא מוצג באפליקציה כהערכה
 * אסטרונומית כללית, לא כפסיקה הלכתית.
 */
public final class DefaultCities {

    public static class City {
        public final String name;
        public final double lat;
        public final double lon;
        City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final City[] ALL = {
            new City("בני ברק", 32.0807, 34.8338),
            new City("בני ברק - מתחם הסופרים", 32.0853, 34.8321),
            new City("ירושלים - רוממה", 31.7876, 35.2007),
            new City("ירושלים - רמת שלמה", 31.8127, 35.2225),
            new City("ירושלים - נווה יעקב", 31.8319, 35.2364),
            new City("ירושלים - גבעת שאול", 31.7867, 35.1926),
            new City("גבעת זאב", 31.8642, 35.1721),
            new City("אלעד", 32.0500, 34.9500),
            new City("תל אביב", 32.0853, 34.7818),
            new City("מודיעין עילית", 31.9337, 35.0480),
            new City("באר שבע", 31.2518, 34.7913),
            new City("נתיבות", 31.4231, 34.5895),
            new City("פתח תקווה", 32.0870, 34.8882),
    };

    private DefaultCities() { }
}
