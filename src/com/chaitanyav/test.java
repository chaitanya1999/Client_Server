package com.chaitanyav;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Chaitanya V
 */
public class test {
    public void submit(Runnable r){
        new Thread(r).start();
    }
    
    public void shutdown(){}
}
