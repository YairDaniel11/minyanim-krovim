package com.meir.minyanimkrovim;

/** רשימת זמני היום (מפתח יציב + תווית) - משמשת את מסך זמני היום ואת ההתראות לזמני היום. */
public final class ZmanimList {

    public static final class Item {
        public final String key;
        public final int labelRes;

        Item(String key, int labelRes) {
            this.key = key;
            this.labelRes = labelRes;
        }
    }

    /** לפי סדר היום. */
    public static final Item[] ALL = {
            new Item("alot161", R.string.zman_alot_hashachar),
            new Item("alot72", R.string.zman_alot_72),
            new Item("netz", R.string.zman_netz_hachama),
            new Item("shemaMga", R.string.zman_sof_shema_mga),
            new Item("shemaGra", R.string.zman_sof_zman_shema),
            new Item("tefilaMga", R.string.zman_sof_tefila_mga),
            new Item("tefilaGra", R.string.zman_sof_zman_tefila),
            new Item("chatzot", R.string.zman_chatzot),
            new Item("minchaGedola", R.string.zman_mincha_gedola),
            new Item("minchaKetana", R.string.zman_mincha_ketana),
            new Item("plag", R.string.zman_plag_hamincha),
            new Item("shkia", R.string.zman_shkiat_hachama),
            new Item("tzeit85", R.string.zman_tzeit_hakochavim),
            new Item("tzeitRt", R.string.zman_tzeit_rt),
    };

    public static int minutes(SunTimes.Zmanim z, String key) {
        switch (key) {
            case "alot161": return z.alotHashachar;
            case "alot72": return z.alot72;
            case "netz": return z.netzHachama;
            case "shemaMga": return z.sofZmanShemaMga;
            case "shemaGra": return z.sofZmanShemaGra;
            case "tefilaMga": return z.sofZmanTefilaMga;
            case "tefilaGra": return z.sofZmanTefilaGra;
            case "chatzot": return z.chatzotHayom;
            case "minchaGedola": return z.minchaGedola;
            case "minchaKetana": return z.minchaKetana;
            case "plag": return z.plagHamincha;
            case "shkia": return z.shkiatHachama;
            case "tzeit85": return z.tzeitHakochavim;
            case "tzeitRt": return z.tzeitRt72;
            default: return -1;
        }
    }

    public static int labelRes(String key) {
        for (Item i : ALL) if (i.key.equals(key)) return i.labelRes;
        return R.string.zmanim_title;
    }

    private ZmanimList() { }
}
