package org.example;

import java.math.BigDecimal;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        BigDecimal a = new BigDecimal(0.1);
        BigDecimal b = new BigDecimal("0.1");
        BigDecimal c = new BigDecimal("10.0");
        BigDecimal d = new BigDecimal("10.00");

        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
        System.out.println(d);
    }
}
