/*===============================================================*
 *  File: PFrame.java                                            *                                                           *
 *  The class for Frame objects. 					     *
 *  Used by SWP and SWE classes.	 			     *
 *  Uses the Packet class			     			         
 *===============================================================*/
public class PFrame {
    /**
     * ************************************************************
     * Class constants
*************************************************************
     */
    public static final int DATA = 0;
    public static final int ACK = 1;
    public static final int NAK = 2;
    public static final String[] KIND = {"DATA", "ACK", "NAK"};
    /*
     * ************************************************************
     * Instance variables (all public for easy reference)
*************************************************************
     */
    public int kind = 0;//The type of this frame, can be Data, ACK or NAK.
    public int ack = 0;//the sequence number of this frame, from 0 to MAX_SEQ.
    public Packet info = new Packet();//the payload (packet).
    public int seq = 0;//the acknowledgement sequence number.
}//End of PFrame Class
