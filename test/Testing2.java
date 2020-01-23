
import com.chaitanyav.Message;
import com.chaitanyav.server.Action;
import com.chaitanyav.server.ClientHandler;
import com.chaitanyav.server.Server;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Chaitanya V
 */
public class Testing2 {
    public static void main(String[] args) {
        Server svr = new Server(25566);
        try {
            svr.setAction("buttonclick", new Action(){
                @Override
                public void execute(ClientHandler hnd, Message msg){
                    int x = (Integer)msg.getData();
                    hnd.sendData("buttonclick-response",x+1);
                }
            });
            svr.start();
        } catch (Exception ex) {
            Logger.getLogger(Testing2.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
