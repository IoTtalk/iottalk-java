package iottalk;

import java.util.*;

public class ColorBase{
    public static String defaultString = "\033[0m";
    public static String dataString = "\033[1;33m";
    
    public static String wrap(String color, String s){
        return color+s+defaultString;
    }
}
