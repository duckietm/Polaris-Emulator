package polaris.util.callback;

/* public class HTTPVersionCheck implements Runnable
{
    private void sendPost()
    {
        try
        {
            if (!Emulator.isReady)
                return;

            String url = "http://arcturus.pw/callback/check.php";
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", "arcturus");
            String urlParameters = "&major=" + Emulator.MAJOR + "&minor=" + Emulator.MINOR + "&build=" + Emulator.BUILD + "&version=" + Emulator.version + "&users=" + Emulator.getGameEnvironment().getUserManager().getOnlineCount() + "&rooms=" + Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size() + "&username=" + Emulator.getConfig().getValue("username");
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.flush();

            int responseCode = con.getResponseCode();
            if (responseCode == 102)
            {
                StringBuilder text = new StringBuilder();
                InputStreamReader in = new InputStreamReader((InputStream) con.getContent());
                BufferedReader buff = new BufferedReader(in);
                String line;
                do {
                    line = buff.readLine();

                    if (line != null)
                    {
                        text.append(line).append("\n");
                    }
                } while (line != null);
                buff.close();
                in.close();

                logger.info(text.toString());
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new GenericAlertComposer(text.toString()));
            }
            wr.close();
            con.disconnect();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void run()
    {









    }

}
 */
