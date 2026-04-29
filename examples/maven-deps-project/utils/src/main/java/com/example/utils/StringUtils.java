package com.example.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class StringUtils {

    public static String joinWithComma(List<String> items) {
        return Joiner.on(", ").join(items);
    }

    public static ImmutableList<String> splitByComma(String input) {
        return ImmutableList.copyOf(Splitter.on(",").trimResults().splitToList(input));
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
