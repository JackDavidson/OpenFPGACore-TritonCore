`timescale 1ns/1ps
module test_core();

reg  clk;    // clock signal
reg  reset;  // the reset for the entire FPGA's programming
reg  io_dta; // the data in for programming the bitstream
reg  io_den; // the data enable for programming the bitstream
reg  [15:0] io_pin; // the 16 input pins
reg  io_reset; // the reset for the lookup tables
wire [15:0] io_pot; // the 16 output pins

FPGACore FPGA_CORE (
        .clk(clk),
        .reset(reset),
        .io_dta(io_dta),
        .io_den(io_den),
        .io_pin(io_pin),
        .io_reset(io_reset),
        .io_pot(io_pot)
    );

initial begin
    clk <= 0;
    reset <= 1; // we have to start off by resetting everything
    io_dta <= 0;
    io_den <= 0;
    io_pin <= 0;
    io_reset <= 0;
end

integer i;
integer j;
integer file;
reg [8:0] c;
reg [10079:0] toProgram;
always @(*)
begin






    #5 clk = 1'b1;
	#5 clk = 1'b0;
    reset = 0; // do a full reset before anything else.

    io_den = 1; // start the programming.



    // Read file a char at a time
    file = $fopen("and.bits", "r");
    c = $fgetc(file);
    i = 0;
    while (c != 9'h1ff) begin
        if (c[7:0] == 8'h30) begin
            $display("Got a 0!");
            toProgram[i] = 0;
        end else begin
            toProgram[i] = 1;
            $display("Got a 1! index: %d", i);
        end

        // cycle the clock to load the bit into the shift register.
        c = $fgetc(file);
        
        i = i + 1;
    end
    
    #5;
    
    for (i=0; i < 10080; i = i + 1) begin
        #5 io_dta = toProgram[i];
        #5 clk = 1'b1;
        #5 clk = 1'b0;


        if (i == 1000)
            $display ("1000 out of 10080 cycles to finish programming");
        else if (i == 4000)
            $display ("4000 out of 10080 cycles to finish programming");
        else if (i == 8000)
            $display ("8000 out of 10080 cycles to finish programming");
        else if (i == 10000)
            $display ("10000 out of 10080 cycles to finish programming");
    end

    io_den = 0;
    #5 io_reset = 1;
    #5 clk = 1'b1;
    #5 clk = 1'b0;
    #5 io_reset = 0;
    #5 $display("expecting io_pot to be FFFF. got: %h", io_pot);
    

    #5 io_pin = 16'h0x000F;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    #5 io_pin = io_pin << 1;
    #5 $display("Input is: %h, Output is: %h", io_pin, io_pot);
    

/*
    #5 clk <= 1'b1;
	#5 clk <= 1'b0;
    reset <= 0; // do a full reset before anything else.

    io_den <= 1; // start the programming.
    
    for (i = 0; i < 240; i=i+1) begin
        io_dta <= 1;
        #5 clk <= 1'b1;
	    #5 clk <= 1'b0;
        io_dta <= 1;
        #5 clk <= 1'b1;
	    #5 clk <= 1'b0;
        for(j = 0; j < 8; j=j+1) begin
            io_dta <= 1;
            #5 clk <= 1'b1;
	        #5 clk <= 1'b0;
            io_dta <= 0;
            #5 clk <= 1'b1;
	        #5 clk <= 1'b0;
        end
    end

    io_den <= 0;
    io_reset <= 1;
    #5 clk <= 1'b1;
    #5 clk <= 1'b0;
    io_reset <= 0;
    #5 $display("expecting io_pot to be FFFF. got: %h", io_pot);


*/

    $display("done.");
    $stop;

end

endmodule
