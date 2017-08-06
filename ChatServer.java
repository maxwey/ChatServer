/**
 * ====== How to use the server ======
 *
 * Launch in command line in the format:
 * java ChatServer [-p port_number] [-l] [-g]
 *
 * -p port_number is the port on which this server will be listening for connetions. The default is 58755.
 * -l flag inidcates that the server will log all screen output to a file. File will be autogenerated with a timestamp.
 * -g flag indicates to the server to launch in no-gui mode. This will make the default standard in and standard out where all console text is shown.
 *    This mode is enabled by default when the current machine does not support GUIs (such as many server machines)
 *
 * Notes about Admin Commands:
 *
 * Admin commands can be called in 2 ways: By entering the command in the server terminal (keyboard input), or by a properly authorized client (with the ADM code ID)
 *    To issue commands from a client, preceed the message with '//' followed by the command to execute.
 *
 * Available Commands & Format:
 *
 *    ADMIN [USER_NAME] [(y)es|(n)o]  -  Makes the specified user(s) admins, so that they can access the command list
 *    PSWD [PASSWORD]                 -  Sets the password to server. Omiting text after PSWD will remove a pre-exiting password. Only new users will need to authenticate.
 *    KICK [USER_NAME] [REASON]       -  Disconnects specified user(s), and sends [REASON] to indicate why they are being disconnected
 *    NOTIFY [MESSAGE]                -  Shows [MESSAGE] as a server notification to all users.
 *    TELL [USER_NAME] [MESSAGE]      -  Shows [MESSAGE] as a server message to the specified user(s) only.
 *    QUIT (MESSAGE)                  -  Closes the server program. If specified, the optional (MESSAGE) is sent to all users.
 *    LIST                            -  Lists all connected users and their IP addresses
 *    HELP                            -  Lists all available commands
 *
 * Where [USER_NAME] is the name, or names (delimited by a ',' character) of the target users. The single character '*' can also be used to indicate all connected users.
 *
 * Passwords can be any character sequence, but must begin and end with non-whitespace characters. Entering ' test ' as a password will result in 'test' being used as
 * the server password. This behavior is intended to avoid accidentally setting the password to have beginning and trailing spaces as they are not always easily identified.
 *
 *
 * FOR NO-GUI MODE: Entering the '\' character into the console will toggle pausing all output. This is inteded to allow for an easier time entering and reading the commands sent.
 * This mode is ideal when the output is causing input to become unreadable. The output will become unpaused by simply sending anoher '\'
 *
 *
 * Notes about message/commands:
 *
 * Every communication between the server and the client start with a 3 letter ID code
 * to inidcate the nature of the request/or what the following information indicates. The 3 letter code is immediately followed by the content (if applicable)
 * There is no extra whitespace between the ID code and the following message. All communications are terminated with a null character ('\0') to deliminate when the
 * message has been completely received.
 *
 * NCR - New Connection Request: sent by client to initiate a connection. The message is immediately followed by a LEGAL username
 *       (1-10 characters comprised of: 'A'-'Z', 'a'-'z', or '_'), a separator character ('\3') and a password. The password field is ignored if the server password isnt set.
 * CON - Connected: sent by the server to the client to acknowledge and confirm the connection request. No additional data is included
 * NCN - Not Connected: sent by the server to the client to indicate that the client was not able to connect with the given information.
 * DSC - Disconnect: sent by client or server to "politely" indicate that the program will disconnect. A reason or message can optionally follow.
 * SND - Send Message: sent by the client to indicate a new message to be sent to the other users. The data that follows it is the message to be sent
 * MSG - Forward Message: sent by the server to the clients to indicate a new message. The data that follows it is the username of the sender, a separator character ('\3') and the message
 * NOT - Server notification: sent by the server to the clients with a message from the server.
 * ADM - Admin Command: sent by client to server to issue an admin command. (The user must have permission to use the admin commands) The message that follows the ADM command will be executed
 * RSP - Server response: optionally sent by server following an ADM command with the response text/output.
 *
 * NOTE: the following serve to notifiy of an issue. The server and clients should be able to recover from an ERC/ERS regardless of their nature
 * The ERC and ERS may be followed by additional text to indicate where/what the error is.
 *
 * ERC - Error Client: sent by the server to the client to indicate that something the client has committed an Error (sent bad data)
 * ERS - Error Server: sent by the client to the server to indicate that something the server has committed an Error (sent bad data)
 *
 *
 * TODO: read server properties from file. E.g. admin IP/username, port settings, log settings.....
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatServer extends Thread {

   public static final float VERSION_NUMBER = 0.1f;

   private boolean logToFile;
   private boolean useGUI;
   private int defaultPort;

   private boolean outputPaused;
   private StringBuilder consoleSave;

   private ServerUI ui;
   private ServerSocket listeningPort;
   private PrintStream logFile;
   private ArrayList<OpenSocket> allConnections;
   private String password;

   public static void main(String[] args) {
      new ChatServer(args);
   }


   public ChatServer(String args[]) {

      //parse arguments
      logToFile = false;
      useGUI = true;
      defaultPort = 58755;

      String usage = "Usage: java ChatServer [-p port_number] [-l] [-g]\n -p port_number: the port on which server listens for connections (integer between 0-65535) default: 8755\n -l will log output to a file\n -g if flag is set, gui will be disabled";

      for(int i = 0; i < args.length; i++) {
         if(args[i].charAt(0) != '-' ) {
            System.out.println(usage);
            System.exit(1);
         }

         switch (args[i].charAt(1)) {

            case 'p':
               try {
                  defaultPort = Integer.parseInt(args[i].substring(2));
               } catch(Exception e) {
                  System.out.println(usage);
                  System.exit(1);
               }
               break;

            case 'l':
               logToFile = true;
               break;

            case 'g':
               useGUI = false;
               break;

            default:
               System.out.println(usage);
               System.exit(1);
         }
      }
      //end argument parsing

      outputPaused = false;
      consoleSave = new StringBuilder(64);

      //create UI (if available)
      if(!GraphicsEnvironment.isHeadless() && useGUI) {
         ui = new ServerUI(this);
      } else {
         ui = null;
         useGUI = false;
      }

      LocalDateTime date = LocalDateTime.now();
      //server set-up
      if(logToFile) { //prepare to log to file
         try {
            String fname = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd__HH.mm.ss")) + ".log";

            logFile = new PrintStream(new File(fname));
         } catch (FileNotFoundException e) {
            System.out.println(e.toString());
         }
      } else {
         logFile = null;
      }

      try {
         listeningPort = new ServerSocket(defaultPort);
      } catch(IOException e) {
         System.out.println(e.toString());
      }
      allConnections = new ArrayList<OpenSocket>();

      password = ""; //set up the password from file here!

      //echo server settings
      print(date.format(DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss")));
      print("Starting ChatServer");
      print("ChatServer version " + ChatServer.VERSION_NUMBER);
      print("Using port number: " + defaultPort);
      print("Logging output to file: " + logToFile);
      print("Password set: " + !password.isEmpty());

      //start server

      //listen to keyboard input
      this.start();

      //start processing new connections
      new Thread(new Runnable() {
         public void run() {
            processConnections();
         }
      }).start();

      print("Server ready.\n=============================================\n");
   }

   /**
    * Will wait until a new connection is requested, at which point
    * it will spawn a new thread to deal with the new connection and resume
    * waiting
    */
   private void processConnections() {
      while(true) {
         try {
            Socket thisSocket = listeningPort.accept();

            new OpenSocket(thisSocket); //create the OpenSocket object, but dont add to the allConnections yet...

         } catch(IOException e) {
            System.out.println(e.toString());
         }
      }
   }

   /**
    * Process the given line as a command interpretation
    *
    * @param  String line          line to parse
    * @return        String        String reprentation of the result. Null on error.
    *                              Empty string when successful, but no output generated.
    */
   private String processCommand(String line) {
      Scanner scan = new Scanner(line);
      if(!scan.hasNext()) {
         return null; //return: no input provided!
      }

      try {

         String next = scan.next();
         if(line.matches("ADMIN ((\\w{1,10}|(\\w{1,10},\\w{1,10})*)|\\*) (y.*|n.*|Y.*|N.*)")) {
            ArrayList<OpenSocket> users = getUsers(scan.next());
            char res = scan.next().charAt(0);
            boolean turnOn;
            if(res == 'y' || res == 'Y') {
               turnOn = true;
            } else if(res == 'n' || res == 'N') {
               turnOn = false;
            } else {
               return "Bad argument. No changes made.";
            }

            if(users.isEmpty()) {
               return "No users matched the query";
            }
            String result = "";
            for(OpenSocket soc : users) {
               result = soc.userName + ' ' + result;
               soc.isAdmin = turnOn;
            }
            return "The users " + result + "have admin access set to " + turnOn;
         } else if(next.equals("PSWD")) {
            if(scan.hasNext()) {
               password = scan.nextLine().trim();
               return "Password set!";
            } else {
               password = "";
               return "Password removed!";
            }
         } else if(line.matches("KICK ((\\w{1,10}|(\\w{1,10},\\w{1,10})*)|\\*) .+")) {
            ArrayList<OpenSocket> users = getUsers(scan.next());
            String message = scan.nextLine();
            if(users.isEmpty()) {
               return "No users matched the query";
            }
            for(OpenSocket soc : users) {
               soc.disconnect('"' + soc.userName + "\" has been kicked from the server\nReason: "+ message, message);
            }
         } else if(line.matches("NOTIFY .+")) {
            String message = scan.nextLine();
            for(OpenSocket soc : allConnections) {
               soc.sendMessageToSelf("NOTServer announcement: " + message + '\0');
            }
         } else if(line.matches("TELL ((\\w{1,10}|(\\w{1,10},\\w{1,10})*)|\\*) .+")) {
            ArrayList<OpenSocket> users = getUsers(scan.next());
            String message = scan.nextLine();
            if(users.isEmpty()) {
               return "No users matched the query";
            }
            for(OpenSocket soc : users) {
               soc.sendMessageToSelf("NOTMessage from server: " + message + '\0');
            }
         } else if(next.equals("QUIT")) {
            String message = "";
            if(scan.hasNextLine()) {
               message = scan.nextLine();
            }
            ArrayList<OpenSocket> copy = new ArrayList<>(allConnections);
            for(OpenSocket soc : copy) {
               soc.disconnect("", message);
            }
            System.exit(0); //quit server program
         } else if (next.equals("LIST")) {
            StringBuilder sb = new StringBuilder();
            if(allConnections.isEmpty()) {
               sb.append(" No connected users");
            }
            for(int i = 0; i < allConnections.size(); i++) {
               OpenSocket soc = allConnections.get(i);
               sb.append(String.format(" %-10s -- %10s", soc.userName, soc.thisSocket.getInetAddress().toString().substring(1)));
               if(i < allConnections.size()-1) {
                  sb.append('\n');
               }
            }
            return sb.toString();
         } else if (next.equals("HELP")) {
            return "Available commands are:\n ADMIN [USER_NAME] [(y)es|(n)o]\n PSWD [PASSWORD]\n KICK [USER_NAME] [REASON]\n NOTIFY [MESSAGE]\n TELL [USER_NAME] [MESSAGE]\n QUIT (MESSAGE)\n LIST\n HELP";
         } else {

            return null;
         }

      } catch(Exception e) { //catch any parse error that may occur.
         e.printStackTrace();
         return null;
      }
      return ""; //assume that it was successful
   }

   private ArrayList<OpenSocket> getUsers(String userString) {

      String[] users = userString.trim().split(",");
      if(users[0].equals("*")) {
         return new ArrayList<>(allConnections);
      }

      ArrayList<OpenSocket> userList = new ArrayList<OpenSocket>();
      for(String s : users) {
         for(OpenSocket soc : allConnections) {
            if(soc.userName.equals(s)) {
               userList.add(soc);
               break;
            }
         }
      }
      return userList;

   }

   /**
    *    ADMIN [USER_NAME] [(y)es|(n)o]  -  Makes the specified user(s) admins, so that they can access the command list
    *    PSWD [PASSWORD]                 -  Sets the password to server. Omiting text after PSWD will remove a pre-exiting password. Only new users will need to authenticate.
    *    KICK [USER_NAME] [REASON]       -  Disconnects specified user(s), and sends [REASON] to indicate why they are being disconnected
    *    NOTIFY [MESSAGE]                -  Shows [MESSAGE] as a server notification to all users.
    *    TELL [USER_NAME] [MESSAGE]      -  Shows [MESSAGE] as a server message to the specified user(s) only.
    *    QUIT (MESSAGE)                  -  Closes the server program. If specified, the optional (MESSAGE) is sent to all users.
    */
   public void run() {
      Scanner scan = new Scanner(System.in);
      String command;
      while(true) {
         try {
            command = scan.nextLine();
         } catch(Exception e) {
            //error in scan is probably due to program exiting. Quit gracefully...
            System.out.println("Closing keyboard input...");
            return;
         }
         if(command.equals("\\")) { //toggles the output pause
            pauseOutput(!outputPaused);
         } else {
            print("Sever console: " + command);
            String output = processCommand(command);
            if(output == null) {
               System.out.println("Bad input / error parsing input");
            } else if(output.length() > 0){
               System.out.println(output);
            }
         }
      }
   }


   /**
    * Pauses the output of the console to allow for typing into the console without
    * having output. Saves all the output, and will print all output to console once
    * unpaused. Note that this only affects the console output. Toggling the pause
    * when GUI is enabled will do nothing.
    * @param boolean flag [description]
    */
   private void pauseOutput(boolean flag) {

      outputPaused = flag;
      if(!flag) { //if unpaused, print saved output.
         System.out.print(consoleSave.toString());
         consoleSave.setLength(0);
      }
   }

   /**
    * Print normal logging text to the output
    * @param String text [description]
    */
   private void print(String text) {
      if(logFile != null) {
         logFile.println(text);
      }
      if(useGUI) {
         ui.addText(text, "normal");
      } else if(outputPaused) {
         consoleSave.append(text);
         consoleSave.append('\n');
      } else {
         System.out.println(text);
      }
   }

   /**
    * Print a text to Error. (different formatting/handling with error messages)
    * @param String text [description]
    */
   private void printE(String text) {
      if(logFile != null) {
         logFile.println("Error: " + text);
      }
      if(useGUI) {
         ui.addText(text, "error");
      } else if(outputPaused) {
         consoleSave.append("Error: ");
         consoleSave.append(text);
         consoleSave.append('\n');
      } else {
         System.out.println("Error: " + text);
      }
   }



   //
   // ==============================================================================================
   // ==============================================================================================
   //
   /**
    * Class to handles the incoming socket!
    */
   private class OpenSocket extends Thread {

      private PrintStream out;
      private BufferedReader in;
      private byte[] buf;
      private int bufSize;
      private int bufItems;
      private boolean willDisconnect;

      public Socket thisSocket;
      public String userName;
      public boolean isAdmin;


      public OpenSocket(Socket socket) {
         super();
         thisSocket = socket;
         try {
            out = new PrintStream(new BufferedOutputStream(thisSocket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(thisSocket.getInputStream()));
         } catch(IOException e) {
            System.out.println(e.toString());
         }

         bufSize = 1024;
         bufItems = 0;
         buf = new byte[bufSize];
         isAdmin = false;
         willDisconnect = false;

         this.start(); //begin the thread
      }


      public void handleMessage() {

         if(bufItems <= 3) { //client has sent a bad command
            out.print("ERCBad command sent\0");
            out.flush();
            return;
         }

         String msg = new String(buf, 0, 3);
         String content = new String(buf, 3, bufItems-4);

         if(msg.equals("NCR")) { //new connection request

            int separator = content.indexOf('\3');
            String pass = null;
            if(separator != -1) {
               pass = content.substring(separator+1);
               content = content.substring(0, separator);
            }

            if(!password.isEmpty()) { //if server password is set
               if(!password.equals(pass)) {
                  out.print("NCNIncorrect password\0");
                  out.flush();
                  willDisconnect = true;
                  closeSocket();
                  return;
               }
            }

            if(!content.matches("\\w{1,10}")) { //check username format
               out.print("NCNClient sent bad username\0");
               out.flush();
               willDisconnect = true;
               closeSocket();
               return;
            }

            for(OpenSocket soc : allConnections) { //check that username is available
               if(soc.userName.equals(content)) {
                  out.print("NCNUsername has already been taken\0");
                  out.flush();
                  willDisconnect = true;
                  closeSocket();
                  return;
               }
            }

            userName = content; //read username
            out.print("CON\0"); //indicate successful connection to the user
            out.flush();

            print("New connection from " + thisSocket.getInetAddress().toString() + " : " + userName);
            sendMessageToOthers("NOTThe user \"" + userName + "\" has connected to the server\0");

            allConnections.add(this); //now that user is connected, add to allConnections

         } else if(msg.equals("SND")) { //new message sent

            sendMessageToOthers("MSG" + userName + '\3' + content + '\0');
            print(userName + ": " + content);


         } else if(msg.equals("DSC")) { //client indicates disconnection

            disconnect(userName + " has disconnected from the server", "");

         } else if(msg.equals("ADM")) {

            if(isAdmin) {
               print("From client " + this.userName + ": " + content);
               String output = processCommand(content);
               if(output == null) {
                  out.print("ERCBad command input\0");
               } else if(output.length() > 0){
                  out.print("RSP" + output + '\0');
               }
            } else {
               out.print("ERCYou do not have admin permissions\0");
            }
            out.flush();

         } else if(msg.equals("ERS")) { //client had an error with the server command
            printE("Client encountered error\n" + content);
         } else {
            printE("Unknown??\n" + msg + "\n" + content);
         }
      }

      /**
       * Send a message to this socket
       * @param String message the message
       */
      public void sendMessageToSelf(String message) {
         try {
            this.out.print(message);
            this.out.flush();
         } catch(Exception e) {
            e.printStackTrace();
         }
      }

      /**
       * Subroutine for sending the specified message to all other users
       * @param String message message to send
       */
      public void sendMessageToOthers(String message) {
         try {
            for(OpenSocket soc : allConnections) {
               if(soc.equals(this)) continue; //dont send back the message to socket that sent message
               soc.out.print(message);
               soc.out.flush();
            }
         } catch(Exception e) {
            e.printStackTrace();
         }
      }

      /**
       * Read the next available message.
       * Remember to reset the buffer size at the end to delete previously read message
       * TODO: consider changing to a StringBuilder...
       */
      public void readMessage() {
         int character;
         try {
            while( (character = in.read()) > 0) { //block on read until some text becomes available
               if(bufItems >= bufSize) {
                  bufSize *= 2;
                  byte[] newBuf = new byte[bufSize];
                  for(int i = 0; i < buf.length; i++) {
                     newBuf[i] = buf[i];
                  }
                  buf = newBuf;
               }
               buf[bufItems] = (byte)character;
               bufItems++;
            }

            if(character == -1) { //in.read() has been shut down, close socket.
               synchronized (this) {
                  try {
                     if(willDisconnect && !(thisSocket.isInputShutdown() && thisSocket.isOutputShutdown()) )
                        this.wait(); //if disconnect is intentional, wait for disconnect method to complete
                  } catch (Exception e) {
                     e.printStackTrace();
                  }
               }
               closeSocket();
               return;
            }

            buf[bufItems++] = '\0'; //set terminator

         } catch(Exception e) {
            e.printStackTrace();
            closeSocket();
         }
      }

      /**
       * Public method to indicate that this connection will terminate.
       * The idea is that this method is /guaranteed/ to complete before
       * closeSocket() is invoked.
       * @param String msgToOthers [description]
       * @param String msgToSelf   [description]
       */
      public void disconnect(String msgToOthers, String msgToSelf) {
         willDisconnect = true;
         if(!msgToOthers.isEmpty()) {
            sendMessageToOthers("NOT" + msgToOthers + '\0');
            print(msgToOthers);
         }
         sendMessageToSelf("DSC" + msgToSelf + '\0');
         try {
            thisSocket.shutdownInput();
            thisSocket.shutdownOutput();
         } catch (Exception ex) {
            ex.printStackTrace();
         }
         synchronized (this) {
            this.notifyAll(); //alert that task is complete
         }
      }

      /**
       * Close the socket and remove the connection.
       */
      private void closeSocket() {
         if(!willDisconnect) { //is this an unexpected disconnection? if so print a message
            printE('"' + userName + "\" has lost connection to the server");
            sendMessageToOthers("NOT\"" + userName + "\" has lost connection to the server\0");
         }
         try {
            thisSocket.close();
            allConnections.remove(this);
         } catch (Exception ex) {
            ex.printStackTrace();
         }
      }

      public void run() {
         while(!thisSocket.isClosed()) {
            //read the message
            readMessage();
            //handle the new message
            handleMessage();
            //reset buf size
            bufItems = 0;
         }
      }

   }
}
