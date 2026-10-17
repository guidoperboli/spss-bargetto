import sys
from instgenerator import SSPInstGenerator
from sspinstance import SSPInstance

def main():
    #try:
    #sspinst = SSPInstance(123456789, 10)
    #sspinst.generateScenarios(6)
    
    # create generator
    sspignr = SSPInstGenerator(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    sspignr.generate()
    
    #except:
    #    print("Error")     
    
    print("Quit")

if __name__ == '__main__':    
    main()
