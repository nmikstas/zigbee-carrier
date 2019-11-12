using System;
using System.IO;
using System.IO.Ports;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Threading;

namespace ZigBee_Monitor
{
    public partial class Form1 : Form
    {
        //Index values into IO data sample RX indicator frame payload array.
        private const int IO_PAYLOAD_FRAME_TYPE = 0, IO_PAYLOAD_SRC_SER_HI = 1,
            IO_PAYLOAD_SRC_SER_LO = 5, IO_PAYLOAD_SRC_NET_ADR = 9,
            IO_PAYLOAD_OPTIONS = 11, IO_PAYLOAD_NUM_SAMPLES = 12,
            IO_PAYLOAD_DIG_MASK = 13, IO_PAYLOAD_AN_MASK = 15;

        //Analog mask values.
        private const int ADC_0 = 0x01, ADC_1 = 0x02, ADC_2 = 0x04,
            ADC_3 = 0x08, ADC_VCC = 0x80;

        //Digital mask values.
        private const int DIO_0 = 0x0001,  DIO_1 = 0x0002,  DIO_2 = 0x0004,
            DIO_3 = 0x0008,  DIO_4 = 0x0010,  DIO_5 = 0x0020,  DIO_6 = 0x0040,
            DIO_7 = 0x0080,  DIO_10 = 0x0400, DIO_11 = 0x0800, DIO_12 = 0x1000;

        //Zigbee frame types.
        private const byte IO_DATA_SAMPLE_RX_INDICATOR_FRAME = (byte)0x92;

        private bool finished = true;
        private bool led1On = false;
        private bool led2On = false;
        private bool led3On = false;
        private bool led4On = false;

        private SerialPort sp;
        Thread readThread;
        byte[] txData = new byte[20];

        private ZFrame zFrame = new ZFrame(); //Stores Zigbee frame data.
        private RX rx = new RX(); //Keep track of frame assembly data.

        //Rx states.
        enum RXStates
        {
            READY,
            LENGTH,
            DATABLOCK,
            PROCESS,
            INVALID
        };

        class RX
        {
            public RXStates rxState { get; set; }
            public byte[] data      { get; set; }
            public int stateCount   { get; set; }
            public int dataIndex    { get; set; }
            public int rxLength     { get; set; }
        
            public RX()
            {
                rxState = RXStates.READY;
                data = new byte[64];
            }
        }

        class ZFrame
        {
            public int length     { get; set; }
            public byte checksum  { get; set; }
            public byte[] payload { get; set; }

            public ZFrame()
            {
                length = 0; //Number of bytes in the payload array.
                checksum = 0;
                payload = new byte[64];
            }
        }

        //Calculate ZigBee frame checksum.
        void setChecksum(int length)
        {
            byte checksum = (byte)0xFF;

            for (int i = 3; i < length - 1; i++)
                checksum -= txData[i];

            txData[length - 1] = checksum;
        }        

        //Data received event handler.
        private void spDataReceived(object sender, SerialDataReceivedEventArgs e)
        {
            int rxCount;
            byte[] rxBytes = new byte[32];
            SerialPort sp = (SerialPort)sender;

            try
            {
                rxCount = sp.Read(rxBytes, 0, rxBytes.Length);
                assembleFrame(rxBytes, rxCount);
            }
            catch (Exception err)
            {
                this.BeginInvoke(new resetApp(doReset)); 
            }            
        }

        //State machine that assembles ZigBee frames from raw serial data.
        private void assembleFrame(byte[] buffer, int length)
        {
            int thisIndex = 0;

            try
            {
                while(thisIndex < length)
                {
                    //Move from READY state to LENGTH state.
                    if(rx.rxState == RXStates.READY && buffer[thisIndex] == 0x7E)
                    {
                        rx.rxState = RXStates.LENGTH;
                        rx.stateCount = 2;
                        rx.rxLength = 0;
                        thisIndex++;
                    }
                    else if(rx.rxState == RXStates.LENGTH)
                    {
                        if(rx.stateCount == 2)
                        { //Get upper byte of packet length.
                            rx.rxLength = buffer[thisIndex];
                            rx.rxLength <<= 8; //Save into upper byte of word.
                            rx.stateCount--;
                            thisIndex++;
                        }
                        else if(rx.stateCount == 1)
                        { //Get lower byte of packet length.
                            rx.rxLength |= buffer[thisIndex] & 0xff; //Save into lower byte of word.
                            rx.stateCount = rx.rxLength;
                            rx.rxState = RXStates.DATABLOCK;
                            thisIndex++;
                        }
                    }
                    else if(rx.rxState == RXStates.DATABLOCK)
                    {
                        rx.data[rx.dataIndex] = buffer[thisIndex];
                        rx.dataIndex++;
                        rx.stateCount--;
                        thisIndex++;
                        if(rx.stateCount == 0)
                            rx.rxState = RXStates.PROCESS;
                    }
                    else if(rx.rxState == RXStates.PROCESS)
                    {
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
                    else
                    { //Invalid state.
                        rx.rxState = RXStates.READY;
                        rx.dataIndex = 0;
                        rx.stateCount = 0;
                        thisIndex++;
                    }
                }
            }
            //Just in case the algorithm goes off the rails (which it will),
            //This will catch it and start over.
            catch (Exception e)
            {
                rx.rxState = RXStates.READY;
                rx.dataIndex = 0;
                rx.stateCount = 0;
                Console.WriteLine("Invalid state");
            }
        }

        void processZFrame()
        {
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

            switch(zFrame.payload[0])
            {
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
                    if(digitalSampleIndex != 0)
                    {
                        digitalSamples = zFrame.payload[digitalSampleIndex];
                        digitalSamples <<= 8;
                        digitalSamples |= zFrame.payload[digitalSampleIndex + 1] & 0xff;
                    }

                    i = 0;
                    //Extract analog samples.
                    if((analogMask & ADC_0) != 0)
                    {
                        adc0 = zFrame.payload[analogSampleIndex + i];
                        adc0 <<= 8;
                        adc0 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                        i += 2;
                    }
                    if((analogMask & ADC_1) != 0)
                    {
                        adc1 = zFrame.payload[analogSampleIndex + i];
                        adc1 <<= 8;
                        adc1 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                        i += 2;
                    }
                    if((analogMask & ADC_2) != 0)
                    {
                        adc2 = zFrame.payload[analogSampleIndex + i];
                        adc2 <<= 8;
                        adc2 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                        i += 2;
                    }
                    if((analogMask & ADC_3) != 0)
                    {
                        adc3 = zFrame.payload[analogSampleIndex + i];
                        adc3 <<= 8;
                        adc3 |= zFrame.payload[analogSampleIndex + i + 1] & 0xff;
                        i += 2;
                    }
                
                    //Update digital I/Os!
                    if ((digitalSamples & DIO_4 & digitalMask) != 0)
                    {
                        label7.Image = ZigBee_Monitor.Properties.Resources.LED_On;
                        led1On = true;
                    }
                    else
                    {
                        label7.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
                        led1On = false;
                    }

                    if ((digitalSamples & DIO_5 & digitalMask) != 0)
                    {
                        label8.Image = ZigBee_Monitor.Properties.Resources.LED_On;
                        led2On = true;
                    }
                    else
                    {
                        label8.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
                        led2On = false;
                    }

                    if ((digitalSamples & DIO_6 & digitalMask) != 0)
                    {
                        label9.Image = ZigBee_Monitor.Properties.Resources.LED_On;
                        led3On = true;
                    }
                    else
                    {
                        label9.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
                        led3On = false;
                    }

                    if ((digitalSamples & DIO_7 & digitalMask) != 0)
                    {
                        label10.Image = ZigBee_Monitor.Properties.Resources.LED_On;
                        led4On = true;
                    }
                    else
                    {
                        label10.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
                        led4On = false;
                    }

                    if((digitalSamples & DIO_10 & digitalMask) != 0)
                        label11.Image = ZigBee_Monitor.Properties.Resources.Switch_On;
                    else
                        label11.Image = ZigBee_Monitor.Properties.Resources.Switch_Off;

                    if((digitalSamples & DIO_11 & digitalMask) != 0)
                        label12.Image = ZigBee_Monitor.Properties.Resources.Switch_On;
                    else
                        label12.Image = ZigBee_Monitor.Properties.Resources.Switch_Off;

                    if((digitalSamples & DIO_12 & digitalMask) != 0)
                        label13.Image = ZigBee_Monitor.Properties.Resources.Button_Pushed;
                    else
                        label13.Image = ZigBee_Monitor.Properties.Resources.Button_Unpushed;

                    //Update analog IOs!   
                    if ((analogMask & ADC_0) != 0)
                    {
                        int result;
                        if(adc0 == 0)
                            result = 0;
                        else
                            result = (adc0 * 120) / 1200;
                        if (result > 100)
                            result = 100;
                           
                        this.BeginInvoke(new SetTB1(TB1DataReceived), new object[] { result }); 
                    }
                    else
                    {
                        this.BeginInvoke(new SetTB1(TB1DataReceived), new object[] { 0 }); 
                    }
                    
                    if((analogMask & ADC_1) != 0) {
                        int result;
                        if (adc1 == 0)
                            result = 0;
                        else
                            result = (adc1 * 120) / 1200;
                        if (result > 100)
                            result = 100;

                        this.BeginInvoke(new SetTB2(TB2DataReceived), new object[] { 100 - result });
                    }
                    else
                        this.BeginInvoke(new SetTB2(TB2DataReceived), new object[] { 100 });

                    if((analogMask & ADC_2) != 0) {
                        int result;
                        if (adc2 == 0)
                            result = 0;
                        else
                            result = (adc2 * 120) / 1200;
                        if (result > 100)
                            result = 100;

                        this.BeginInvoke(new SetPB1(PB1DataReceived), new object[] { result });
                    }
                    else
                        this.BeginInvoke(new SetPB1(PB1DataReceived), new object[] { 0 });
                    
                    if((analogMask & ADC_3) != 0) {
                        int mv = (adc3 * 1200) / 1024;
                        float degC = mv / 10.0f;
                        float degF = degC * 1.8f + 32;

                        int degFInt = (int)(degF *= 10);                   
                        degF = degFInt / 10.0f;

                        this.BeginInvoke(new SetTemp(tempDataReceived), new object[] { 
                            String.Format("{0:0.0}", degC), String.Format("{0:0.0}", degF) });
                    }
                    else {
                        this.BeginInvoke(new SetTemp(tempDataReceived), new object[] { "N/A", "N/A" });
                    }

                    //Setup tx destination addresses and networks.
                    txData[5]  = zFrame.payload[1];
                    txData[6]  = zFrame.payload[2];
                    txData[7]  = zFrame.payload[3];
                    txData[8]  = zFrame.payload[4];
                    txData[9]  = zFrame.payload[5];
                    txData[10] = zFrame.payload[6];
                    txData[11] = zFrame.payload[7];
                    txData[12] = zFrame.payload[8];

                    txData[13] = zFrame.payload[9];
                    txData[14] = zFrame.payload[10];
                    
                    break;

                default:
                    break;
            }

            for(int j = 0; j < rx.rxLength; j++)
                Console.Write(String.Format("{0:X2} ", rx.data[j]));
            Console.WriteLine();
        }

        //Delegate functions for allowing non UI threads update UI threads.
        private delegate void SetTB1(int value);
        private void TB1DataReceived(int value)
        {
            trackBar1.Value = value;
        }

        private delegate void SetTB2(int value);
        private void TB2DataReceived(int value)
        {
            trackBar2.Value = value;
        }

        private delegate void SetPB1(int value);
        private void PB1DataReceived(int value)
        {            
            float multiplier = (float)label23.Height / 99.0f;
            label24.Height = label23.Height - (int)(value * multiplier);            
        }

        private delegate void SetTemp(string degC, string degF);
        private void tempDataReceived(string degC, string degF)
        {
            textBox1.Text = degC;
            textBox2.Text = degF;
        }

        private delegate void resetApp();
        private void doReset()
        {
            try
            {
                finished = true;
                if (sp.IsOpen)
                    sp.Close();
                readThread.Abort();
            }
            catch (Exception err) { }

            button3.Enabled = false;
            button2.Enabled = true;
            button1.Enabled = true;
            comboBox1.Enabled = true;
            comboBox2.Enabled = true;
            comboBox3.Enabled = true;
            comboBox4.Enabled = true;
            comboBox5.Enabled = true;
            comboBox6.Enabled = true;
            button4.Enabled = false;
            button5.Enabled = false;
            button6.Enabled = false;
            button7.Enabled = false;
            textBox1.Text = "N/A";
            textBox2.Text = "N/A";
            label7.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
            label8.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
            label9.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
            label10.Image = ZigBee_Monitor.Properties.Resources.LED_Off;
            label24.Height = label23.Height / 2;
            trackBar1.Value = 50;
            trackBar2.Value = 50;
            label11.Image = ZigBee_Monitor.Properties.Resources.Switch_Off;
            label12.Image = ZigBee_Monitor.Properties.Resources.Switch_Off;
            label13.Image = ZigBee_Monitor.Properties.Resources.Button_Unpushed;
        }

        private void connect()
        {
            //Create a new SerialPort object with default settings.
            sp = new SerialPort();

            //Configure the serial port.
            sp.PortName = comboBox1.SelectedItem.ToString();            
            sp.BaudRate = int.Parse(comboBox2.SelectedItem.ToString());
            sp.DataBits = int.Parse(comboBox3.SelectedItem.ToString());

            if (comboBox5.SelectedIndex == 1)
                sp.StopBits = StopBits.OnePointFive;
            else if (comboBox5.SelectedIndex == 2)
                sp.StopBits = StopBits.Two;
            else
                sp.StopBits = StopBits.One;

            if (comboBox4.SelectedIndex == 1)
                sp.Parity = Parity.Odd;
            else if (comboBox4.SelectedIndex == 2)
                sp.Parity = Parity.Even;
            else if (comboBox4.SelectedIndex == 3)
                sp.Parity = Parity.Mark;
            else if (comboBox4.SelectedIndex == 4)
                sp.Parity = Parity.Space;
            else
                sp.Parity = Parity.None;

            sp.ReadTimeout  = 500;
            sp.WriteTimeout = 500;

            if(!sp.IsOpen)
                sp.Open();
            finished = false;
            sp.DataReceived += new SerialDataReceivedEventHandler(spDataReceived);
        }

        public Form1()
        {            
            InitializeComponent();
            label24.Height = label23.Height / 2;
        }

        private void Form1_Load(object sender, EventArgs e)
        {
            comboBox2.SelectedIndex = 2;
            comboBox3.SelectedIndex = 3;
            comboBox4.SelectedIndex = 0;
            comboBox5.SelectedIndex = 0;
            comboBox6.SelectedIndex = 0;

            //Enumerate com ports.
            foreach (string s in SerialPort.GetPortNames())        
                comboBox1.Items.Add(s);            

            if (comboBox1.Items.Count > 0)
                comboBox1.SelectedIndex = 0;
        }

        private void Form1_FormClosing(object sender, FormClosingEventArgs e)
        {
            finished = true;
            doReset();
        }

        private void button1_Click(object sender, EventArgs e)
        {
            comboBox1.Items.Clear();
            //Re-enumerate com ports.
            foreach (string s in SerialPort.GetPortNames())           
                comboBox1.Items.Add(s);            

            if (comboBox1.Items.Count > 0)
                comboBox1.SelectedIndex = 0;
        }

        private void button2_Click(object sender, EventArgs e)
        {
            try
            {
                connect();                
                button3.Enabled = true;
                button2.Enabled = false;
                button1.Enabled = false;
                comboBox1.Enabled = false;
                comboBox2.Enabled = false;
                comboBox3.Enabled = false;
                comboBox4.Enabled = false;
                comboBox5.Enabled = false;
                comboBox6.Enabled = false;
                button4.Enabled = true;
                button5.Enabled = true;
                button6.Enabled = true;
                button7.Enabled = true;
            }
            catch (Exception err)
            {
                MessageBox.Show(err.ToString(), "Error Opening COM Port", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void button3_Click(object sender, EventArgs e)
        {
            doReset();
        }

        private void button4_Click(object sender, EventArgs e)
        {
            txData[0] = 0x7E; //Start of frame.

            txData[1] = 0x00; //Frame length.
            txData[2] = 0x10;

            txData[3] = 0x17; //Frame type.

            txData[4] = 0x00; //Frame ID.

            //Addresses set up in rx method.

            txData[15] = 0x02; //Apply changes.

            txData[16] = 0x44; //D
            txData[17] = 0x34; //4

            if (led1On)
                txData[18] = 0x04; //Digital output, low.
            else
                txData[18] = 0x05; //Digital output, high.

            setChecksum(20);

            try
            {
                sp.Write(txData, 0, 20);
            }
            catch (TimeoutException) { }
        }

        private void button5_Click(object sender, EventArgs e)
        {
            txData[0] = 0x7E; //Start of frame.

            txData[1] = 0x00; //Frame length.
            txData[2] = 0x10;

            txData[3] = 0x17; //Frame type.

            txData[4] = 0x00; //Frame ID.

            //Addresses set up in rx method.

            txData[15] = 0x02; //Apply changes.

            txData[16] = 0x44; //D
            txData[17] = 0x35; //5

            if (led2On)
                txData[18] = 0x04; //Digital output, low.
            else
                txData[18] = 0x05; //Digital output, high.

            setChecksum(20);

            try
            {
                sp.Write(txData, 0, 20);
            }
            catch (TimeoutException) { }
        }

        private void button6_Click(object sender, EventArgs e)
        {
            txData[0] = 0x7E; //Start of frame.

            txData[1] = 0x00; //Frame length.
            txData[2] = 0x10;

            txData[3] = 0x17; //Frame type.

            txData[4] = 0x00; //Frame ID.

            //Addresses set up in rx method.

            txData[15] = 0x02; //Apply changes.

            txData[16] = 0x44; //D
            txData[17] = 0x36; //6

            if (led3On)
                txData[18] = 0x04; //Digital output, low.
            else
                txData[18] = 0x05; //Digital output, high.

            setChecksum(20);

            try
            {
                sp.Write(txData, 0, 20);
            }
            catch (TimeoutException) { }
        }

        private void button7_Click(object sender, EventArgs e)
        {
            txData[0] = 0x7E; //Start of frame.

            txData[1] = 0x00; //Frame length.
            txData[2] = 0x10;

            txData[3] = 0x17; //Frame type.

            txData[4] = 0x00; //Frame ID.

            //Addresses set up in rx method.

            txData[15] = 0x02; //Apply changes.

            txData[16] = 0x44; //D
            txData[17] = 0x37; //7

            if (led4On)
                txData[18] = 0x04; //Digital output, low.
            else
                txData[18] = 0x05; //Digital output, high.

            setChecksum(20);

            try
            {
                sp.Write(txData, 0, 20);
            }
            catch (TimeoutException) { }
        }
    }
}
