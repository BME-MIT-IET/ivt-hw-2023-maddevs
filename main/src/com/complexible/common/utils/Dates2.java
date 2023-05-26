package com.complexible.common.utils;

import com.complexible.common.base.Dates;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Dates2 {

    public static Date asDate(final String theDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse(theDate);
        } catch (ParseException pe) {
            throw new RuntimeException("Unable to parse date string: " + theDate, pe);
        }
    }

    public static String datetimeISO(Date theDate) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(theDate);
    }
}
