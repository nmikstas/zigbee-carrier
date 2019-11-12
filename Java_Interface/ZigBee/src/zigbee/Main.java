package zigbee;

import javax.swing.*;
import java.awt.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Main extends JFrame{
    //Index values into IO data sample RX indicator frame payload array.
    private static final int IO_PAYLOAD_FRAME_TYPE = 0, IO_PAYLOAD_SRC_SER_HI = 1,
            IO_PAYLOAD_SRC_SER_LO = 5, IO_PAYLOAD_SRC_NET_ADR = 9,
            IO_PAYLOAD_OPTIONS = 11, IO_PAYLOAD_NUM_SAMPLES = 12,
            IO_PAYLOAD_DIG_MASK = 13, IO_PAYLOAD_AN_MASK = 15;

    //Analog mask values.
    private static final int ADC_0 = 0x01, ADC_1  = 0x02, ADC_2 = 0x04,
            ADC_3 = 0x08, ADC_VCC = 0x80;

    //Digital mask values.
    private static final int DIO_0 = 0x0001,  DIO_1 = 0x0002,  DIO_2 = 0x0004,
            DIO_3 = 0x0008,  DIO_4 = 0x0010,  DIO_5 = 0x0020,  DIO_6 = 0x0040,
            DIO_7 = 0x0080,  DIO_10 = 0x0400, DIO_11 = 0x0800, DIO_12 = 0x1000;

    //Zigbee frame types.
    private static final byte IO_DATA_SAMPLE_RX_INDICATOR_FRAME = (byte)0x92;

    private static final int APP_WIDTH = 750, APP_HEIGHT = 450,
            COMPANEL_START_X = 0, COMPANEL_START_Y = 0, COMPANEL_WIDTH = 744, COMPANEL_HEIGHT = 85,
            DEVPANEL_START_X = 0, DEVPANEL_START_Y = 85, DEVPANEL_WIDTH = 744, DEVPANEL_HEIGHT = 335;

    private boolean finished = true;

    private CommPort commPort;

    private ZFrame zFrame = new ZFrame(); //Stores Zigbee frame data.
    private RX rx = new RX(); //Keep track of frame assembly data.
    private TX tx = new TX(); //Create buffer to transmit Zigbee frames.

    private JPanel comPanel, devicePanel;
    private JLabel comPortsLabel, baudLabel, dataBitsLabel, parityLabel, stopBitsLabel,
            flowControlLabel, led1GFXLabel, led2GFXLabel, led3GFXLabel, led4GFXLabel,
            led1Label, led2Label, led3Label, led4Label, switch3GFXLabel, switch4GFXLabel,
            switch3Label, switch4Label, btn1GFXLabel, btn1Label, adcXLabel, adcYLabel,
            stickLabel, potLabel, tempSensorLabel, curTempLabel, cLabel, fLabel,
            devicesLabel;
    private JButton comPortsBtn, connectBtn, disconnectBtn, led1Btn, led2Btn,
            led3Btn, led4Btn, devicesBtn;
    private JComboBox comPortsBox, baudBox, dataBitsBox, parityBox, stopBitsBox,
            flowControlBox, devicesBox;
    private JTextField cField, fField;
    private JSlider adcXSlider, adcYSlider;
    private JProgressBar adcBar;
    private ImageIcon ledOff, ledOn, switchOff, switchOn, btnUnpushed, btnPushed;

    //Rx states.
    enum RXStates {
        READY,
        LENGTH,
        DATABLOCK,
        PROCESS,
        INVALID
    };

    public Main() {
        super("AvidCore Systems - ZigBee Expansion Board Monitor");

        setLayout(null);
        try { //Set look and feel to Windows look and feel.
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        }
        catch(Exception e) {System.err.println("Error updating UI: " + e);}

        setSize(APP_WIDTH, APP_HEIGHT);
        setLocationRelativeTo(null); //Center JFrame on Desktop.
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

/***********************************Communications Panel***********************************/

        comPanel = new JPanel();
        comPanel.setLayout(null);        

        comPanel.setBounds(COMPANEL_START_X, COMPANEL_START_Y, COMPANEL_WIDTH, COMPANEL_HEIGHT);
        comPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1), "Communications Setup"));

        comPortsLabel = new JLabel("Available Com Ports:");
        comPortsLabel.setBounds(15, 20, 100, 20);
        comPanel.add(comPortsLabel);

        comPortsBox = new JComboBox();
        comPortsBox.setBounds(120, 20, 100, 20);
        GetComPorts(); //Populate combo box with available com ports.
        comPanel.add(comPortsBox);

        comPortsBtn = new JButton("Refresh Com Ports");
        comPortsBtn.setBounds(240, 20, 130, 20);
        comPanel.add(comPortsBtn);

        connectBtn = new JButton("Connect");
        connectBtn.setBounds(390, 20, 100, 20);
        comPanel.add(connectBtn);

        disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setBounds(510, 20, 100, 20);
        disconnectBtn.setEnabled(false);
        comPanel.add(disconnectBtn);

        baudLabel = new JLabel("Baud Rate:");
        baudLabel.setBounds(58, 50, 70, 20);
        comPanel.add(baudLabel);

        baudBox = new JComboBox();
        baudBox.setBounds(120, 50, 70, 20);
        baudBox.addItem(1200);
        baudBox.addItem(2400);
        baudBox.addItem(4800);
        baudBox.addItem(9600);
        baudBox.addItem(19200);
        baudBox.addItem(38400);
        baudBox.addItem(57600);
        baudBox.addItem(115200);
        baudBox.addItem(230400);
        baudBox.setSelectedIndex(3);
        comPanel.add(baudBox);

        dataBitsLabel = new JLabel("Data Bits:");
        dataBitsLabel.setBounds(210, 50, 70, 20);
        comPanel.add(dataBitsLabel);

        dataBitsBox = new JComboBox();
        dataBitsBox.setBounds(263, 50, 40, 20);
        dataBitsBox.addItem(5);
        dataBitsBox.addItem(6);
        dataBitsBox.addItem(7);
        dataBitsBox.addItem(8);
        dataBitsBox.setSelectedIndex(3);
        comPanel.add(dataBitsBox);

        parityLabel = new JLabel("Parity:");
        parityLabel.setBounds(323, 50, 70, 20);
        comPanel.add(parityLabel);

        parityBox = new JComboBox();
        parityBox.setBounds(360, 50, 70, 20);
        parityBox.addItem("NONE");
        parityBox.addItem("ODD");
        parityBox.addItem("EVEN");
        parityBox.addItem("MARK");
        parityBox.addItem("SPACE");
        comPanel.add(parityBox);

        stopBitsLabel = new JLabel("Stop Bits:");
        stopBitsLabel.setBounds(450, 50, 50, 20);
        comPanel.add(stopBitsLabel);

        stopBitsBox = new JComboBox();
        stopBitsBox.setBounds(500, 50, 50, 20);
        stopBitsBox.addItem(1);
        stopBitsBox.addItem(1.5);
        stopBitsBox.addItem(2);
        comPanel.add(stopBitsBox);

        flowControlLabel = new JLabel("Flow Control:");
        flowControlLabel.setBounds(570, 50, 70, 20);
        comPanel.add(flowControlLabel);

        flowControlBox = new JComboBox();
        flowControlBox.setBounds(639, 50, 90, 20);
        flowControlBox.addItem("NONE");
        flowControlBox.addItem("HARDWARE");
        flowControlBox.addItem("SOFTWARE");
        comPanel.add(flowControlBox);

/***************************************Device Panel***************************************/

        devicePanel = new JPanel();
        devicePanel.setLayout(null);
        
        devicePanel.setBounds(DEVPANEL_START_X, DEVPANEL_START_Y, DEVPANEL_WIDTH, DEVPANEL_HEIGHT);
        devicePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.BLACK, 1), "Device Status"));

        ledOff = new ImageIcon("./Graphics/LED_Off.png");
        ledOn  = new ImageIcon("./Graphics/LED_On.png");

        switchOff = new ImageIcon("./Graphics/Switch_Off.png");
        switchOn  = new ImageIcon("./Graphics/Switch_On.png");

        btnUnpushed = new ImageIcon("./Graphics/Button_Unpushed.png");
        btnPushed   = new ImageIcon("./Graphics/Button_Pushed.png");

        stickLabel = new JLabel("<html><b>ANALOG STICK</b></html>");
        stickLabel.setBounds(118, 50, 150, 40);
        devicePanel.add(stickLabel);

        adcXSlider = new JSlider(javax.swing.SwingConstants.HORIZONTAL, 0, 100, 50);
        adcXSlider.setBounds(85, 103, 150, 50);
        adcXSlider.setPaintTicks(true);
        adcXSlider.setMajorTickSpacing(50);
        adcXSlider.setMinorTickSpacing(10);
        adcXSlider.setInverted(true);
        adcXSlider.setEnabled(false);
        devicePanel.add(adcXSlider);

        adcXLabel = new JLabel("X-AXIS (AD0)");
        adcXLabel.setBounds(130, 87, 75, 20);
        devicePanel.add(adcXLabel);

        adcYSlider = new JSlider(javax.swing.SwingConstants.VERTICAL, 0, 100, 50);
        adcYSlider.setBounds(25, 40, 50, 150);
        adcYSlider.setPaintTicks(true);
        adcYSlider.setMajorTickSpacing(50);
        adcYSlider.setMinorTickSpacing(10);
        adcYSlider.setInverted(true);
        adcYSlider.setEnabled(false);
        devicePanel.add(adcYSlider);

        adcYLabel = new JLabel("Y-AXIS (AD1)");
        adcYLabel.setBounds(35, 25, 75, 20);
        devicePanel.add(adcYLabel);

        adcBar = new JProgressBar(javax.swing.SwingConstants.VERTICAL, 0, 100);
        adcBar.setValue(50);
        adcBar.setBounds(280, 40, 20, 150);
        devicePanel.add(adcBar);

        potLabel = new JLabel("<html>POTENTIOMETER (AD2)</html>");
        potLabel.setBounds(310, 100, 100, 40);
        devicePanel.add(potLabel);

        tempSensorLabel = new JLabel("TEMPERATURE SENSOR (AD3)");
        tempSensorLabel.setBounds(440, 80, 150, 40);
        devicePanel.add(tempSensorLabel);

        curTempLabel = new JLabel("CURRENT TEMPERATURE:");
        curTempLabel.setBounds(448, 100, 150, 40);
        devicePanel.add(curTempLabel);

        cField = new JTextField();
        cField.setBounds(490, 135, 40, 20);
        cField.setText("N/A");
        cField.setEditable(false);
        devicePanel.add(cField);

        cLabel = new JLabel("C");
        cLabel.setBounds(540, 135, 20, 20);
        devicePanel.add(cLabel);

        fField = new JTextField();
        fField.setBounds(490, 170, 40, 20);
        fField.setText("N/A");
        fField.setEditable(false);
        devicePanel.add(fField);

        fLabel = new JLabel("F");
        fLabel.setBounds(540, 170, 20, 20);
        devicePanel.add(fLabel);

        led1Label = new JLabel("LED1 (DIO4)");
        led1Label.setBounds(25, 215, 70, 20);
        devicePanel.add(led1Label);

        led1GFXLabel = new JLabel(ledOff);
        led1GFXLabel.setBounds(5, 215, 100, 100);
        devicePanel.add(led1GFXLabel);

        led1Btn = new JButton("Toggle LED1");
        led1Btn.setEnabled(false);
        led1Btn.setBounds(10, 300, 95, 20);
        devicePanel.add(led1Btn);

        led2Label = new JLabel("LED2 (DIO5)");
        led2Label.setBounds(145, 215, 70, 20);
        devicePanel.add(led2Label);

        led2GFXLabel = new JLabel(ledOff);
        led2GFXLabel.setBounds(125, 215, 100, 100);
        devicePanel.add(led2GFXLabel);

        led2Btn = new JButton("Toggle LED2");
        led2Btn.setEnabled(false);
        led2Btn.setBounds(130, 300, 95, 20);
        devicePanel.add(led2Btn);
   
        led3Label = new JLabel("LED3 (DIO6)");
        led3Label.setBounds(265, 215, 70, 20);
        devicePanel.add(led3Label);

        led3GFXLabel = new JLabel(ledOff);
        led3GFXLabel.setBounds(245, 215, 100, 100);
        devicePanel.add(led3GFXLabel);

        led3Btn = new JButton("Toggle LED3");
        led3Btn.setEnabled(false);
        led3Btn.setBounds(250, 300, 95, 20);
        devicePanel.add(led3Btn);

        led4Label = new JLabel("LED4 (DIO7)");
        led4Label.setBounds(385, 215, 70, 20);
        devicePanel.add(led4Label);

        led4GFXLabel = new JLabel(ledOff);
        led4GFXLabel.setBounds(365, 215, 100, 100);
        devicePanel.add(led4GFXLabel);

        led4Btn = new JButton("Toggle LED4");
        led4Btn.setEnabled(false);
        led4Btn.setBounds(370, 300, 95, 20);
        devicePanel.add(led4Btn);

        switch3Label = new JLabel("SWITCH3 (DIO10)");
        switch3Label.setBounds(505, 215, 100, 20);
        devicePanel.add(switch3Label);

        switch3GFXLabel = new JLabel(switchOff);
        switch3GFXLabel.setBounds(485, 230, 100, 100);
        devicePanel.add(switch3GFXLabel);

        switch4Label = new JLabel("SWITCH4 (DIO11)");
        switch4Label.setBounds(635, 215, 100, 20);
        devicePanel.add(switch4Label);

        switch4GFXLabel = new JLabel(switchOff);
        switch4GFXLabel.setBounds(615, 230, 100, 100);
        devicePanel.add(switch4GFXLabel);

        btn1Label = new JLabel("BUTTON1 (DIO12)");
        btn1Label.setBounds(630, 100, 100, 20);
        devicePanel.add(btn1Label);

        btn1GFXLabel = new JLabel(btnUnpushed);
        btn1GFXLabel.setBounds(620, 110, 100, 100);
        devicePanel.add(btn1GFXLabel);

/*************************************Action Listeners*************************************/

        comPortsBtn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                    comPortsBox.removeAllItems();
                    GetComPorts(); //Re-enumerate the com ports.
                }
            }
        );

        connectBtn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {    
                    try {
                        finished = false;
                        connect();
                        disconnectBtn.setEnabled(true);
                        connectBtn.setEnabled(false);
                        comPortsBtn.setEnabled(false);
                        comPortsBox.setEnabled(false);
                        baudBox.setEnabled(false);
                        dataBitsBox.setEnabled(false);
                        parityBox.setEnabled(false);
                        stopBitsBox.setEnabled(false);
                        flowControlBox.setEnabled(false);
                        led1Btn.setEnabled(true);
                        led2Btn.setEnabled(true);
                        led3Btn.setEnabled(true);
                        led4Btn.setEnabled(true);
                    }
                    catch(Exception err) {
                        JOptionPane.showMessageDialog(comPanel.getParent(), err,
                                "Error Opening Com Port", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        );

        disconnectBtn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                    try {
                        finished = true;
                        commPort.close();
                        disconnectBtn.setEnabled(false);
                        connectBtn.setEnabled(true);
                        comPortsBtn.setEnabled(true);
                        comPortsBox.setEnabled(true);
                        baudBox.setEnabled(true);
                        dataBitsBox.setEnabled(true);
                        parityBox.setEnabled(true);
                        stopBitsBox.setEnabled(true);
                        flowControlBox.setEnabled(true);
                        led1Btn.setEnabled(false);
                        led2Btn.setEnabled(false);
                        led3Btn.setEnabled(false);
                        led4Btn.setEnabled(false);
                        cField.setText("N/A");
                        fField.setText("N/A");
                        led1GFXLabel.setIcon(ledOff);
                        led2GFXLabel.setIcon(ledOff);
                        led3GFXLabel.setIcon(ledOff);
                        led4GFXLabel.setIcon(ledOff);
                        adcBar.setValue(50);
                        adcXSlider.setValue(50);
                        adcYSlider.setValue(50);
                        switch3GFXLabel.setIcon(switchOff);
                        switch4GFXLabel.setIcon(switchOff);
                        btn1GFXLabel.setIcon(btnUnpushed);
                    }
                    catch(Exception err) {
                        JOptionPane.showMessageDialog(comPanel.getParent(), err,
                                "Error Closing Com Port", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        );

        led1Btn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                   tx.data[0]  = 0x7E; //Start of frame.
                   
                   tx.data[1]  = 0x00; //Frame length.
                   tx.data[2]  = 0x10;
                   
                   tx.data[3]  = 0x17; //Frame type.

                   tx.data[4]  = 0x00; //Frame ID.

                   //Addresses set up in rx method.

                   tx.data[15] = 0x02; //Apply changes.

                   tx.data[16] = 0x44; //D
                   tx.data[17] = 0x34; //4

                   if(led1GFXLabel.getIcon() == ledOn)
                       tx.data[18] = 0x04; //Digital output, low.
                   else
                       tx.data[18] = 0x05; //Digital output, high.

                   setChecksum(20);
                   
                   tx.txLength = 20;
                   tx.dataToSend = true;
                }
            }
        );

        led2Btn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                    tx.data[0]  = 0x7E; //Start of frame.

                   tx.data[1]  = 0x00; //Frame length.
                   tx.data[2]  = 0x10;

                   tx.data[3]  = 0x17; //Frame type.

                   tx.data[4]  = 0x00; //Frame ID.

                   //Addresses set up in rx method.

                   tx.data[15] = 0x02; //Apply changes.

                   tx.data[16] = 0x44; //D
                   tx.data[17] = 0x35; //5

                   if(led2GFXLabel.getIcon() == ledOn)
                       tx.data[18] = 0x04; //Digital output, low.
                   else
                       tx.data[18] = 0x05; //Digital output, high.

                   setChecksum(20);

                   tx.txLength = 20;
                   tx.dataToSend = true;
                }
            }
        );

        led3Btn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                    tx.data[0]  = 0x7E; //Start of frame.

                   tx.data[1]  = 0x00; //Frame length.
                   tx.data[2]  = 0x10;

                   tx.data[3]  = 0x17; //Frame type.

                   tx.data[4]  = 0x00; //Frame ID.

                   //Addresses set up in rx method.

                   tx.data[15] = 0x02; //Apply changes.

                   tx.data[16] = 0x44; //D
                   tx.data[17] = 0x36; //6

                   if(led3GFXLabel.getIcon() == ledOn)
                       tx.data[18] = 0x04; //Digital output, low.
                   else
                       tx.data[18] = 0x05; //Digital output, high.

                   setChecksum(20);

                   tx.txLength = 20;
                   tx.dataToSend = true;
                }
            }
        );

        led4Btn.addActionListener(
            new ActionListener() {
                public void actionPerformed (ActionEvent e) {
                    tx.data[0]  = 0x7E; //Start of frame.

                   tx.data[1]  = 0x00; //Frame length.
                   tx.data[2]  = 0x10;

                   tx.data[3]  = 0x17; //Frame type.

                   tx.data[4]  = 0x00; //Frame ID.

                   //Addresses set up in rx method.

                   tx.data[15] = 0x02; //Apply changes.

                   tx.data[16] = 0x44; //D
                   tx.data[17] = 0x37; //7

                   if(led4GFXLabel.getIcon() == ledOn)
                       tx.data[18] = 0x04; //Digital output, low.
                   else
                       tx.data[18] = 0x05; //Digital output, high.

                   setChecksum(20);

                   tx.txLength = 20;
                   tx.dataToSend = true;
                }
            }
        );

/****************************************Add Panels****************************************/

        add(comPanel);
        add(devicePanel);
    }

/************************************Enumerate Com Ports***********************************/

    private void GetComPorts() {

        java.util.Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();

        while (portEnum.hasMoreElements()) {
            CommPortIdentifier portIdentifier = portEnum.nextElement();
            if(portIdentifier.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                comPortsBox.addItem(portIdentifier.getName());
            }
        }
    }

/********************************Connect To The Serial Port********************************/

    private void connect() throws Exception {
        int baudRate, dataBits, stopBits, parityBits;

        //Get string name of serial port.
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(
                comPortsBox.getSelectedItem().toString());

        //Check if port is already in use.  If so, display a message and exit.
        if(portIdentifier.isCurrentlyOwned()) {
            JOptionPane.showMessageDialog(comPanel.getParent(), "Com Port Is Already In Use",
                                "Com Port Is Already In Use", JOptionPane.WARNING_MESSAGE);
            return;
        }
             
        commPort = portIdentifier.open(this.getClass().getName(),2000);
        SerialPort serialPort = (SerialPort) commPort;

        baudRate = (Integer) baudBox.getSelectedItem();

        if(dataBitsBox.getSelectedIndex() == 0)
            dataBits = SerialPort.DATABITS_5;
        else if(dataBitsBox.getSelectedIndex() == 1)
            dataBits = SerialPort.DATABITS_6;
        else if(dataBitsBox.getSelectedIndex() == 2)
            dataBits = SerialPort.DATABITS_7;
        else
            dataBits = SerialPort.DATABITS_8;

        if(stopBitsBox.getSelectedIndex() == 1)
            stopBits = SerialPort.STOPBITS_1_5;
        else if(stopBitsBox.getSelectedIndex() == 2)
            stopBits = SerialPort.STOPBITS_2;
        else
            stopBits = SerialPort.STOPBITS_1;

        if(parityBox.getSelectedIndex() == 1)
            parityBits = SerialPort.PARITY_ODD;
        else if(parityBox.getSelectedIndex() == 2)
            parityBits = SerialPort.PARITY_EVEN;
        else if(parityBox.getSelectedIndex() == 3)
            parityBits = SerialPort.PARITY_MARK;
        else if(parityBox.getSelectedIndex() == 4)
            parityBits = SerialPort.PARITY_SPACE;
        else
            parityBits = SerialPort.PARITY_NONE;

        serialPort.setSerialPortParams(baudRate, dataBits, stopBits, parityBits);

        InputStream in = serialPort.getInputStream();
        OutputStream out = serialPort.getOutputStream();

        (new Thread(new SerialReader(in))).start();
        (new Thread(new SerialWriter(out))).start();   
    }

/************************************Serial Reader Class***********************************/

    class SerialReader implements Runnable {

        InputStream in;

        public SerialReader (InputStream in) {
            this.in = in;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int len = -1;

            try {
                while ( ( len = this.in.read(buffer)) > -1) {
                    if(finished) //Exit if disconnect has been clicked.
                        return;
                    
                    assembleFrame(buffer, len);
                }
            }
            catch ( IOException e ) {
                e.printStackTrace();
            }
        }
    }

/************************************Serial Writer Class***********************************/

    class SerialWriter implements Runnable {

        OutputStream out;

        public SerialWriter(OutputStream out) {
            this.out = out;
        }

        public void run() {
            try {
                while (tx.txLength > -1) {
                    if(finished) //Exit if disconnect has been clicked.
                        return;

                    try{
                        Thread.currentThread().sleep(100);//sleep for 100 ms
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }
                    
                    if(tx.dataToSend) { //Send any data in tx buffer.
                        this.out.write(tx.data, 0, tx.txLength);
                        tx.dataToSend = false;
                    }
                }
            }
            catch ( IOException e ) {
                e.printStackTrace();
            }
        }
    }

/******************************************************************************************/
    class ZFrame {

        int length;
        byte checksum;
        byte[] payload;

        public ZFrame() {
            length = 0; //Number of bytes in the payload array.
            checksum = 0;
            payload = new byte[64];
        }
    }

/******************************************************************************************/

    class RX {
        RXStates rxState;
        byte data[];
        int stateCount;
        int dataIndex;
        int rxLength;
        
        public RX() {
            rxState = RXStates.READY;
            data = new byte[64];
        }
    }

/******************************************************************************************/

    class TX {
        byte data[];
        int txLength;
        boolean dataToSend;
        
        public TX() {
            data = new byte[64];
            txLength = 0;
            dataToSend = false;
        }
    }

/******************************************************************************************/

    void assembleFrame(byte[] buffer, int length) {
        int thisIndex = 0;

        try {
            while(thisIndex < length) {
                //Move from READY state to LENGTH state.
                if(rx.rxState == RXStates.READY && buffer[thisIndex] == 0x7E) {
                    rx.rxState = RXStates.LENGTH;
                    rx.stateCount = 2;
                    rx.rxLength = 0;
                    thisIndex++;
                }
                else if(rx.rxState == RXStates.LENGTH) {
                    if(rx.stateCount == 2) { //Get upper byte of packet length.
                        rx.rxLength = buffer[thisIndex];
                        rx.rxLength <<= 8; //Save into upper byte of word.
                        rx.stateCount--;
                        thisIndex++;
                    }
                    else if(rx.stateCount == 1) { //Get lower byte of packet length.
                        rx.rxLength |= buffer[thisIndex] & 0xff; //Save into lower byte of word.
                        rx.stateCount = rx.rxLength;
                        rx.rxState = RXStates.DATABLOCK;
                        thisIndex++;
                    }
                }
                else if(rx.rxState == RXStates.DATABLOCK) {
                    rx.data[rx.dataIndex] = buffer[thisIndex];
                    rx.dataIndex++;
                    rx.stateCount--;
                    thisIndex++;
                    if(rx.stateCount == 0)
                        rx.rxState = RXStates.PROCESS;
                }
                else if(rx.rxState == RXStates.PROCESS) {
                    zFrame.checksum = buffer[thisIndex]; //Get checksum.
                    //Fill zFrame data members.
                    zFrame.length = rx.rxLength;
                    for(int i = 0; i < rx.rxLength; i++)
                        zFrame.payload[i] = rx.data[i];

                    //Do something with zFrame data.
                    processZFrame();

                    //Clear variables for next packet.
                    rx.rxState = RXStates.READY;
                    rx.dataIndex = 0;
                    rx.stateCount = 0;
                    for(int i = 0; i < rx.rxLength; i++)
                        rx.data[i] = 0; //Reset data array.
                }
                else { //Invalid state.
                    rx.rxState = RXStates.READY;
                    rx.dataIndex = 0;
                    rx.stateCount = 0;
                    thisIndex++;
                }
            }
        }
        //Just in case the algorithm goes off the rails (which it will),
        //This will catch it and start over.
        catch (Exception e) {
            rx.rxState = RXStates.READY;
            rx.dataIndex = 0;
            rx.stateCount = 0;
            System.out.println("Invalid state");
        }
    }

/******************************************************************************************/

    void processZFrame() {
        int digitalMask = 0;
        int analogMask = 0;
        int digitalSampleIndex;
        int analogSampleIndex;
        int digitalSamples = 0;
        int adc0 = 0;
        int adc1 = 0;
        int adc2 = 0;
        int adc3 = 0;
        int i;

        switch(zFrame.payload[0]) {
            case IO_DATA_SAMPLE_RX_INDICATOR_FRAME:
                //Get digital mask.
                digitalMask = zFrame.payload[IO_PAYLOAD_DIG_MASK];
                digitalMask <<= 8;
                digitalMask |= zFrame.payload[IO_PAYLOAD_DIG_MASK + 1] & 0xff;
                
                //Get analog mask.
                analogMask = zFrame.payload[IO_PAYLOAD_AN_MASK];

                //Calculate index for digital values.
                if(digitalMask != 0)
                    digitalSampleIndex = IO_PAYLOAD_AN_MASK + 1;
                else
                    digitalSampleIndex = 0;

                //Calculate index for analog values.
                if(digitalMask != 0)
                    analogSampleIndex = digitalSampleIndex + 2;
                else
                    analogSampleIndex = IO_PAYLOAD_AN_MASK + 1;

                //Extract digital samples (if any).
                if(digitalSampleIndex != 0) {
                    digitalSamples = zFrame.payload[digitalSampleIndex];
                    digitalSamples <<= 8;
                    digitalSamples |= zFrame.payload[digitalSampleIndex + 1] & 0xff;
                }

                i = 0;
                //Extract analog samples.
                if((analogMask & ADC_0) != 0) {
                    adc0 = zFrame.payload[analogSampleIndex + i];
                    adc0 <<= 8;
                    adc0 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                    i += 2;
                }
                if((analogMask & ADC_1) != 0) {
                    adc1 = zFrame.payload[analogSampleIndex + i];
                    adc1 <<= 8;
                    adc1 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                    i += 2;
                }
                if((analogMask & ADC_2) != 0) {
                    adc2 = zFrame.payload[analogSampleIndex + i];
                    adc2 <<= 8;
                    adc2 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                    i += 2;
                }
                if((analogMask & ADC_3) != 0) {
                    adc3 = zFrame.payload[analogSampleIndex + i];
                    adc3 <<= 8;
                    adc3 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                    i += 2;
                }
                
                //Update digital I/Os!
                if((digitalSamples & DIO_4 & digitalMask) != 0)
                    led1GFXLabel.setIcon(ledOn);
                else
                    led1GFXLabel.setIcon(ledOff);

                if((digitalSamples & DIO_5 & digitalMask) != 0)
                    led2GFXLabel.setIcon(ledOn);
                else
                    led2GFXLabel.setIcon(ledOff);

                if((digitalSamples & DIO_6 & digitalMask) != 0)
                    led3GFXLabel.setIcon(ledOn);
                else
                    led3GFXLabel.setIcon(ledOff);

                if((digitalSamples & DIO_7 & digitalMask) != 0)
                    led4GFXLabel.setIcon(ledOn);
                else
                    led4GFXLabel.setIcon(ledOff);

                if((digitalSamples & DIO_10 & digitalMask) != 0)
                    switch3GFXLabel.setIcon(switchOn);
                else
                    switch3GFXLabel.setIcon(switchOff);

                 if((digitalSamples & DIO_11 & digitalMask) != 0)
                    switch4GFXLabel.setIcon(switchOn);
                else
                    switch4GFXLabel.setIcon(switchOff);

                 if((digitalSamples & DIO_12 & digitalMask) != 0)
                    btn1GFXLabel.setIcon(btnPushed);
                else
                    btn1GFXLabel.setIcon(btnUnpushed);

                //Update analog IOs!
                if((analogMask & ADC_0) != 0) {
                    adcXSlider.setValue((adc0 * 120)/1200);
                }
                else
                    adcXSlider.setValue(0);

                if((analogMask & ADC_1) != 0) {
                    adcYSlider.setValue((adc1 * 120)/1200);
                }
                else
                    adcYSlider.setValue(0);

                if((analogMask & ADC_2) != 0) {
                    adcBar.setValue((adc2 * 120)/1200);
                }
                else
                    adcBar.setValue(0);

                if((analogMask & ADC_3) != 0) {
                    int mv = (adc3 * 1200) / 1024;
                    float degC = mv / 10.0f;
                    float degF = degC * 1.8f + 32;

                    int degFInt = (int)(degF *= 10);                   
                    degF = degFInt / 10.0f;

                    cField.setText(Float.toString(degC));
                    fField.setText(Float.toString(degF));
                }
                else {
                    cField.setText("N/A");
                    fField.setText("N/A");
                }

                //Setup tx destination addresses and networks.
                tx.data[5]  = zFrame.payload[1];
                tx.data[6]  = zFrame.payload[2];
                tx.data[7]  = zFrame.payload[3];
                tx.data[8]  = zFrame.payload[4];
                tx.data[9]  = zFrame.payload[5];
                tx.data[10] = zFrame.payload[6];
                tx.data[11] = zFrame.payload[7];
                tx.data[12] = zFrame.payload[8];

                tx.data[13] = zFrame.payload[9];
                tx.data[14] = zFrame.payload[10];

                break;

            default:
                break;
        }

        for(int j = 0; j < rx.rxLength; j++) {
            System.out.printf("%02x ", rx.data[j]);
        }
        System.out.println();
    }

/******************************************************************************************/

    void setChecksum(int length) {
        byte checksum = (byte)0xFF;

        for(int i = 3; i < length - 1; i++)
            checksum -= tx.data[i];

        tx.data[length - 1] = checksum;
    }

/******************************************************************************************/

    public static void main ( String[] args ) {

        Main zigbee = new Main();
        //Change to Windows look and feel.
        SwingUtilities.updateComponentTreeUI(zigbee);
        zigbee.setVisible(true);
    }
}

