package com.dotpet.pet;

import java.time.LocalTime;

public final class PetGreetings {
    private PetGreetings() { }
    public static String forTime(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 5 && hour < 12) return "좋은 아침이야! 오늘도 잘 부탁해!";
        if (hour >= 12 && hour < 18) return "좋은 오후야! 잠깐 쉬어 가도 좋아.";
        if (hour >= 18 && hour < 22) return "좋은 저녁이야! 오늘도 수고했어.";
        return "늦은 시간이네. 편안한 밤 보내!";
    }
}
