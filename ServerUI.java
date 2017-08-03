import javax.swing.*;
import javax.swing.text.DefaultCaret;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.Style;
import java.awt.*;


public class ServerUI extends JFrame {

   private static final int MAX_LINES = 500;
   private int numLines;

   private ChatServer server;
   private JScrollPane contentPanel;
   private JTextPane textArea;
   private StyledDocument textDoc;

   public ServerUI(ChatServer server) {
      contentPanel = new JScrollPane();
      this.setContentPane(contentPanel);

      textArea = new JTextPane();
      textArea.setEditable(false);
      textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
      ((DefaultCaret)textArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
      textDoc = textArea.getStyledDocument();
      Style s = textDoc.addStyle("error", null); //create an error style
      StyleConstants.setForeground(s, Color.RED);
      textDoc.addStyle("normal", null); //create the normal text style

      contentPanel.setViewportView(textArea);

      this.setSize(800, 900);
      this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      this.setVisible(true);

      numLines = 0;
   }


   public void addText(String text, String type) {

      int numLinesInText = 1;
      for(int i = 0; i < text.length(); i++) {
         if(text.charAt(i) == '\n') {
            numLines++;
         }
      }

      int extraLines = (numLines += numLinesInText) - MAX_LINES;
      try {
         if(extraLines > 0) {
            for(int i = 0; i < extraLines; i++) {
               int pos = 0;
               while(!textDoc.getText(pos, 1).equals("\n") && pos < textDoc.getLength()) {
                  pos++;
               }
               textDoc.remove(0, pos+1);
            }
            numLines -= extraLines;
         }

         textDoc.insertString(textDoc.getLength(), text + '\n', textDoc.getStyle(type));
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

}
