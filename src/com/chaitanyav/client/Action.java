package com.chaitanyav.client;

import com.chaitanyav.Message;

/**
 *
 * @author Chaitanya V
 */
public abstract class Action{  
    public abstract void execute(Client client, Message msg);
}
