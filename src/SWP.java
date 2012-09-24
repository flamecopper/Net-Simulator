/**
 * @author goh
 */
import java.util.Timer;
import java.util.TimerTask;

public class SWP {
    public static final int MAX_SEQ = 7; // should be 2n - 1
    public static final int NR_BUFS = (MAX_SEQ + 1) / 2;//NUMBER OF BUFFERS
    
   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];
   boolean[] arrived = new boolean[NR_BUFS]; 
    //the following are used for simulation purpose only
    private SWE swe = null;
    private String sid = null;

    //Constructor
    public SWP(SWE sw, String s) {
        swe = sw;
        sid = s;
    }
    //the following methods are all protocol related
    private void init() {
        for (int i = 0; i < NR_BUFS; i++) {
            out_buf[i] = new Packet();
        }
    }

    private void wait_for_event(PEvent e) {
        swe.wait_for_event(e); //may be blocked
        oldest_frame = e.seq;  //set timeout frame seq
    }

    private void enable_network_layer(int nr_of_bufs) {
        //network layer is permitted to send if credit is available
        swe.grant_credit(nr_of_bufs);
    }

    private void from_network_layer(Packet p) {
        swe.from_network_layer(p);
    }

    private void to_network_layer(Packet packet) {
        swe.to_network_layer(packet);
    }

    private void to_physical_layer(PFrame fm) {
        System.out.println("SWP: Sending frame: seq = " + fm.seq
                + " ack = " + fm.ack + " kind = "
                + PFrame.KIND[fm.kind] + " info = " + fm.info.data);
        System.out.flush();
        swe.to_physical_layer(fm);
    }

    private void from_physical_layer(PFrame fm) {
        PFrame fm1 = swe.from_physical_layer();
        fm.kind = fm1.kind;
        fm.seq = fm1.seq;
        fm.ack = fm1.ack;
        fm.info = fm1.info;
    }
    
    //implement my mehtods here
    
    boolean no_nak = true; // no acknowledgement request
    private Packet in_buf[] = new Packet[NR_BUFS]; // buffers for the inbound stream
    Timer[] f_timer = new Timer[NR_BUFS];
    Timer ack_timer;
    
    public void disable_network_layer(){
        //network layer is not permitted to send if nothing is avaliable
        swe.grant_credit(0);
    }
    
     public void send_frame(int framekind, int frame_nr, int frame_expected, Packet[] buffer) {
        /*construct and send a data,ack,nak frame*/
        PFrame frame = new PFrame();
        frame.kind = framekind;
        if (framekind == PFrame.DATA) {
            frame.info = buffer[frame_nr % NR_BUFS];
        }
        frame.seq = frame_nr;
        frame.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
        if (framekind == PFrame.DATA) {
            no_nak = false;
        }
        to_physical_layer(frame);
        if (framekind == PFrame.DATA) {
            start_timer(frame_nr);
        }
        stop_ack_timer();
    }

    
    public static int inc(int num) {
        num = ((num + 1) % (MAX_SEQ + 1));
        return num;
    }

    private static boolean between(int seq_nr_a, int seq_nr_b, int seq_nr_c) {
        int a = seq_nr_a;
        int b = seq_nr_b;
        int c = seq_nr_c;
        return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
    }
   
    //implement the protocol 6 on selective repeat here
    public void protocol6() {
        init();
        int ack_expected = 0;
        int next_frame_to_send = 0;
        int frame_expected = 0;
        int too_far = NR_BUFS;//winodw size
        
        //grant credits to the network layer based on the window size
        enable_network_layer(NR_BUFS);
        PFrame frame = new PFrame();//scratch variable
        
        int i;//index of the buffer pools
        for(i = 0;i < NR_BUFS;i++){
            arrived[i] = false;
        }
        
        while(true) {	
         wait_for_event(event);
	   switch(event.type) {
	      case (PEvent.NETWORK_LAYER_READY):
                  from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
                  //send a frame when the data is ready
                  send_frame(PFrame.DATA,next_frame_to_send,frame_expected,out_buf);
                  next_frame_to_send = inc(next_frame_to_send);
                  break; 
	      case (PEvent.FRAME_ARRIVAL ):
		  from_physical_layer(frame);
                  //start of acknowledgement
                  if(frame.kind == PFrame.DATA){
                   if((frame.seq != frame_expected) && no_nak){
                           send_frame(PFrame.NAK,0,frame_expected,out_buf);   
                  }else{
                      start_ack_timer();
                  }
                        //check if falls between the sliding windows
                  if(between(frame_expected,frame.seq,too_far) && arrived[frame.seq % NR_BUFS] == false){
                            //accept the frame in any order
                            arrived[frame.seq % NR_BUFS] = true;//mark buffer as full
                            in_buf[frame.seq % NR_BUFS] =  frame.info;
                            while(arrived[frame_expected % NR_BUFS]){
                                to_network_layer(in_buf[frame_expected % NR_BUFS]);
                                no_nak = true;
                                arrived[frame_expected % NR_BUFS] = false;
                                frame_expected = inc(frame_expected);
                                too_far = inc(too_far);
                                start_ack_timer();  
                            }
                  }
                  }
                  if((frame.kind == PFrame.NAK) && between(ack_expected,inc(frame.ack),next_frame_to_send)){
                      send_frame(PFrame.DATA,inc(frame.ack),next_frame_to_send,out_buf);
                  }
                  while(between(ack_expected,frame.ack,next_frame_to_send)){
                      stop_timer(ack_expected % NR_BUFS);
                      ack_expected =  inc(ack_expected);
                      enable_network_layer(1); 
                     
                  }
                  break;	   
              case (PEvent.CKSUM_ERR): 
                   if(no_nak){ 
                       send_frame(PFrame.NAK,0,frame_expected,out_buf);
                   }
      	           break;  
              case (PEvent.TIMEOUT): 
	          send_frame(PFrame.DATA,oldest_frame,frame_expected,out_buf); 
                  break; 
	      case(PEvent.ACK_TIMEOUT): 
                   send_frame(PFrame.ACK,0,frame_expected,out_buf); /*ack timer expired;send ack */
                   break; 
                default: 
		   System.out.println("SWP: undefined event type = " + event.type); 
		   System.out.flush();
            }
      }      
   }
    
   private void start_timer(int seq) {
       stop_timer(seq);
        //create new timer and new timertask
        f_timer[seq % NR_BUFS] = new Timer();
        //schedule the  task for execution after 200ms
        f_timer[seq % NR_BUFS].schedule(new f_task(seq), 200);
   }	

   private void stop_timer(int seq) {
        if (f_timer[seq % NR_BUFS] != null) {
            f_timer[seq % NR_BUFS].cancel();
            f_timer[seq % NR_BUFS] = null;
        }
   }
   private void start_ack_timer( ) {
        stop_ack_timer();

        //starts another timer for sending separate ack
        ack_timer = new Timer();
        ack_timer.schedule(new ack_task(), 50);
   }

   private void stop_ack_timer() {
        if (ack_timer != null) {
            ack_timer.cancel();
            ack_timer = null;
        }
   }
    class ack_task extends TimerTask {

        public void run() {
            //stop timer
            stop_ack_timer();
            swe.generate_acktimeout_event();
        }
    }

    class f_task extends TimerTask {

        private int seq;

        public f_task(int seq) {
            this.seq = seq;
        }

        public void run() {
            //stops this timer, discarding any scheduled tasks for the current seq
            stop_timer(seq);
            swe.generate_timeout_event(seq);
        }
    }
    //create test cases from the main method

    public static void main(String[] args) {
        /*test method and the class*/
        int[] position = {1, 2, 3, 4, 5, 6, 7};
        System.out.println("Current Next");
        for (int i = 0; i < position.length; i++) {
            System.out.println(position[i] + " " + inc(position[i]));
        }
        System.out.println("Running Test Case to check if in between the values");
        int[][] inbetween = {{1, 2, 9}, {1, 2, 4}, {1, 10, 6}, {7, 6, 3}};
        for (int a = 0; a < inbetween.length; a++) {
            int[] value = inbetween[a];
            System.out.println(value[0] + " " + value[1] + " " + value[2]
                    + " " + between(value[0], value[1], value[2]));
        }
    }
}
