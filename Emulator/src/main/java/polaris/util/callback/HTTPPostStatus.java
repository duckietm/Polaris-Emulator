/* package polaris.util.callback;

import polaris.Emulator;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

 public class HTTPPostStatus implements Runnable
{
    private void sendPost() throws Exception
    {
        String url = "http://arcturus.pw/callback/status.php";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", "arcturus");
        String urlParameters = "users=" + Emulator.getGameEnvironment().getUserManager().getOnlineCount() + "&rooms=" + Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size() + "&username=" + Emulator.getConfig().getValue("username") + "&version=" + Emulator.version;
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();
        int responseCode = con.getResponseCode();
        con.disconnect();
    }

    @Override
    public void run()
    {
        try
        {
            this.sendPost();
        }
        catch (Exception e)
        {
        }
    }
}
*/
