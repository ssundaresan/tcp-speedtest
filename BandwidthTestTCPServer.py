import SocketServer
import datetime as dt
from datetime import datetime
import time
import threading
import socket
import string
import random
import sys


# Class to handle connections. Server has no notion of
# requests from same client. Each request is treated
# indpendently. PACKET_SIZE and MAXDUR are overwritten
# by the client.

class StreamHandler(SocketServer.StreamRequestHandler):
  NUMARR = 10
  timeout = 10
  nbytes = 0
  duration = 0
  err = ""

  # Receiver. receives data, computes per second throughput
  # and sends digest to client
 
  def recvData(self):
    cur_thread = threading.current_thread() # fork thread
    start = False 
    end = False
    nbytes = 0
    prevcheck = 0
    prevbytes = 0
    starttime = 0
    endtime = 0
    bysecres = {}
    currsec = 0
    while end == False:
      buf = bytearray(self.PACKET_SIZE) #receive buffer
      mv = memoryview(buf)
      try:
        recret = self.request.recv_into(mv,self.PACKET_SIZE)
      except:
         print "timeout?", recret, "buf",buf[:10] #some error in receive
         self.err += "data_timeout,"
         break
      if recret > 0: #we got data
        if buf[0] == 126: # ~ denotes start of stream
          starttime = datetime.now()
          #print "starting ", starttime, time.mktime(starttime.timetuple())
          start = True
          prevcheck = starttime
        if buf[recret-1] == 43: # + denotes end of stream
          end = True
          endtime = datetime.now()
          #print "ending ", endtime, time.mktime(endtime.timetuple())
          if start:
            duration = endtime - starttime
            duration = duration.seconds + duration.microseconds/1000000.0
      else:
        #print "lt 0"
        self.err += "recv_lt_0,"
        break

      if start:
        nbytes += recret
        ct = datetime.now()
        cdur = ct - starttime
        #print cdur.microseconds,reportgran
        if ((cdur.seconds*1000000 + cdur.microseconds)/self.reportgran)*self.reportgran > currsec and currsec/1000000 < self.MAXDUR: #for per-sec report
          tmpct = ct - prevcheck
          bysecres[currsec/self.reportgran] = (nbytes - prevbytes)/(125.0*(tmpct.seconds + tmpct.microseconds/1000000.0))
          prevbytes = nbytes
          prevcheck = ct
          #print 2,currsec,currsec/reportgran,cdur.microseconds,bysecres[currsec/reportgran]
          currsec = currsec + self.reportgran

    if start and end: # valid test
      #print "valid test for duration ", duration
      #print "Received %d bytes in %f seconds."%(nbytes,duration)
      self.nbytes = nbytes
      self.duration = duration
    else:
      self.err += "invalid_test,"

    if end and start: # generate per-sec report
      out = nbytes/(125.0*duration)
      out = "%s %.2f %s "%(nbytes,duration,out)
      for i in bysecres:
        out = "%s%s:%.2f;"%(out,i,bysecres[i])
    try:
      out = out[:-1]
      print out
      self.request.sendall(out)
    except:
      self.err += "send_timeout,"

  # Send stream. Data stream is random. Starts with ~ and ends with +.

  def sendData(self,msgArr):
    global random_packet_size
    time.sleep(0.1)
    print "DW"
    #fillerStr = "1"
    desired_msgArr = []
    # if the client does not ask for fully randomized stream
    if(self.randomized == 0):
      # if packet size is less than our locally generated random file
      if(self.PACKET_SIZE < random_packet_size):
        for item in msgArr:
              desired_msgArr.append(item[:self.PACKET_SIZE])
      else:
      # if we have generated 1MB random file and the packet size is 10Mb
      # we will staple 10 , 1MB packets
          numberof_small_pkts = self.PACKET_SIZE/random_packet_size
          rest_of_packet = self.PACKET_SIZE - numberof_small_pkts*(random_packet_size)
          for item in msgArr:
              desired_msgArr.append(numberof_small_pkts*item[:self.PACKET_SIZE])
          if(rest_of_packet != 0 ):
            for j in range(len(desired_msgArr)): # generate 1 MB of random stream
                    desired_msgArr[j]= desired_msgArr[j]+ ''.join(random.choice(string.letters+string.digits)  for i in range(rest_of_packet))
          print(len(desired_msgArr[0]))

    # Client wants fully randomized stream
    else:
        for i in range(self.NUMARR): # generate 1 MB of random stream
          desired_msgArr.append(''.join(random.choice(string.letters+string.digits) for i in range(self.PACKET_SIZE)))

    cur_thread = threading.current_thread()
    try:
      self.request.sendall("~") # start
    except:
      self.err += "send_timeout,"
      return
    starttime = datetime.now()
    duration = 0
    totbytes = 0
    endtime = starttime + dt.timedelta(0,self.MAXDUR)
    #print starttime,endtime
    cntmsg = 0
    while True: # loop random data
      try:
          self.request.sendall(desired_msgArr[cntmsg%self.NUMARR])
          cntmsg = cntmsg + 1
      except:
        self.err += "send_err,"
        break
      totbytes += len(desired_msgArr[0])
      duration = datetime.now()# - starttime
      if duration > endtime:
        break
    duration = datetime.now() - starttime
    try:
      self.request.sendall("+")
    except:
      self.err += "send_timeout,"
      return
    self.nbytes = totbytes
    self.duration = duration.seconds + duration.microseconds/1000000.0
    print "Sent %d bytes in %f seconds"%(totbytes,(duration.seconds + duration.microseconds/1000000.0))
    try:
      self.data = self.rfile.readline().strip()
    except:
      print "timeout?"
    print self.data
      
  def handle(self):
    global msgArr
    try:
      self.data = self.rfile.readline().strip()
    except:
      print "timeout?"
      return
    #print "Received ",self.data
    testparam = {}
    for p in self.data.split():
      p = p.split(":")
      testparam[p[0]] = p[1]

    try:
      # These are the variables sent from the client
      self.MAXDUR = int(testparam["duration"])/1000
      self.PACKET_SIZE = int(testparam["pktsize"])
      self.reportgran = int(testparam["reportgran"])*1000 #100000 #microsec
      self.randomized = int(testparam["randomized"])
      if testparam["test"] == "UPLINK":
        self.recvData()
      if testparam["test"] == "DOWNLINK":
        self.sendData(msgArr)
    except:
      pass
    print "Request:%s.%s test:%s time:%s durationreq:%s msgsize:%s nbytes:%s durationtst:%s err:%s"\
          %(self.request.getpeername()[0],self.request.getpeername()[1],testparam["test"],\
            time.time(),self.MAXDUR,self.PACKET_SIZE,self.nbytes,self.duration,self.err)
    self.request.close()
class ThreadedStreamServer(SocketServer.ThreadingMixIn,SocketServer.TCPServer):
  pass


if __name__ == "__main__":
  HOST = ""
  PORT = 9999
  global msgArr
  msgArr = []
  global random_packet_size
  random_packet_size = 1000000 #1MB 
  for i in range(10): # generate a random_packet_size Bytes of random stream
      msgArr.append(''.join(random.choice(string.letters+string.digits) for i in range(random_packet_size)))
  print('created packet, it is now safe to connect')
  #SocketServer.socket.SO_SNDBUF = 146988
  #SocketServer.socket.SO_RCVBUF = 146988
  server = ThreadedStreamServer((HOST,PORT),StreamHandler)
  server.serve_forever()
