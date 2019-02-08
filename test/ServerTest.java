
import com.chaitanyav.Action;
import com.chaitanyav.ClientHandler;
import com.chaitanyav.Message;
import com.chaitanyav.Server;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Chaitanya V
 */
public class ServerTest {
    public static void main(String[] args) {
        String testStr="I am message";
        try {
            Server svr = new Server(25565);
            svr.setAction("ABCD", new Action() {
                @Override
                public void execute(ClientHandler hnd, Message msg) {
                    assert(String.valueOf(msg.getData()).equals(testStr));
                }
            });
            svr.start();
            Socket sock = new Socket("localhost",25565);
            Thread.sleep(2500);
            ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
            oos.writeObject(new Message("ABCD", testStr));
            oos.flush();
            Thread.sleep(2500);
            svr.stop();
        } catch (IOException ex) {
            Logger.getLogger(ServerTest.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(ServerTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
