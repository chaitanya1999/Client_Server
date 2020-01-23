/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chaitanyav;

import java.io.PrintWriter;

/**
 *
 * @author Chaitanya V
 */
public class Utils {
    private Utils(){}
    
    
    static boolean doLog = true;
    public static void disableConsoleLogs(){doLog=false;}
    public static void enableConsoleLogs(){doLog=true;}
    public static void log(Object o){
        if(doLog) System.out.println(o);
    }
}
