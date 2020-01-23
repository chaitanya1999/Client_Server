
import com.chaitanyav.server.ClientHandler;
import com.chaitanyav.Message;
import com.chaitanyav.client.Client;
import com.chaitanyav.server.Server;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

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
        JFrame frm = new JFrame();
        frm.setLayout(new FlowLayout());
        JLabel lbl = new JLabel("0");
        JLabel lbl2 = new JLabel();
        frm.add(lbl);
        JButton button = new JButton("Click");
        JButton button2 = new JButton("disconnect");
        frm.add(button);
        frm.add(button2);
        frm.add(lbl2);
        frm.setSize(100, 100);
        frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frm.setVisible(true);
        
        Server svr = new Server(25566);
        Client clt = new Client("localhost", 25566, 10000, 500){
            @Override
            public void onDisconnect(){
                System.out.println("Disconnected");
            }
            
            @Override
            public void onConnect(){
                System.out.println("Connected");
                lbl2.setText(""+getLocalPort());
            }
        };
        try {
            svr.setAction("buttonclick", new com.chaitanyav.server.Action() {
                @Override
                public void execute(ClientHandler hnd, Message msg) {
                    hnd.sendData("buttonclick-response",((Integer)msg.getData())+1);
                }
            });
            clt.setAction("buttonclick-response",new com.chaitanyav.client.Action(){
                @Override
                public void execute(Client client, Message msg) {
                    lbl.setText(""+(Integer)msg.getData());
                }                
            });
            button.addActionListener((evt)->{
                clt.sendData("buttonclick", Integer.parseInt(lbl.getText()));
            });
            button2.addActionListener((new ActionListener() {
                boolean x=true;
                @Override
                public void actionPerformed(ActionEvent e) {
                    if(x)clt.stop();
                    else clt.start();
                    x=!x;
                }
            }));
            svr.disableHeartbeat();
            svr.start();
            clt.disableHeartbeat();
            clt.setToAutoConnect(7);
            clt.start();
        } catch (Exception ex) {
            Logger.getLogger(ServerTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
