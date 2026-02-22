the code here has part peaces from get 
1) gold data from charles api using schwab-java-client. 
2) feed it to trading enfine 20260220_151510_591pnl_22dd_60wr.jar (integration.md). while feed to the jar u have to know the data get from api is completed candle or not. only completed candle can feed to jar. u have to keep track of last candle date feed to jar so that know get the new data from api and feed the new data to jar. the current code thread start at beginning of minute so that u can get the completed candle data with a not completed candle data from api so u have to find out which remaining data and completed data to trading engine 
3) control the tos program for trading using csharp. Need to have a test for the csharp to test on controlling the tos program for trading. to make sure the coordinates are correct.
4) ignore the code where printscreen to get data from screen. we get data from charles api. 
