#!/usr/bin/env python3
#Usage: python testCaseExtractor.py 'experiment_run-id' 'path-to-targetmodule'
import  pymysql, sys, parse, os.path

def testCaseEquals(testcase1,testcase2):
    if testcase1[0] == testcase2[0] and testcase1[1] == testcase2[1] and testcase1[2] == testcase2[2]:
        return True
    else:
        return False

def testCaseNotInList(testcase,tList):
    for case in tList:
        if testCaseEquals(case,testcase):
            return False
    return True

def removeDuplicates(testcases):
    out = []
    numCases = len(testcases)
    while numCases:
        case = testcases.pop()
        if testCaseNotInList(case,out):
            out.append(case)
        numCases = numCases - 1
    return out

# get id of the experiment run as argument
if len(sys.argv) != 3 or not sys.argv[1].isdigit() and os.path.isfile(sys.argv[2]):
    print("Usage: python testCaseExtractor.py 'experiment_run-id' 'path-to-targetmodule'")
    sys.exit()
id = sys.argv[1]
targetModule = sys.argv[2]

# connect to grinder db
db = pymysql.connect(host='127.0.0.1', port=3306, user='grinder', passwd='grinder', db='grinder')
cursor = db.cursor()

# get log from the chosen experiment run
cursor.execute("SELECT log FROM experiment_run WHERE id = " + id + ";")
log = cursor.fetchone()

# extract testcase information and insert into db
splitLog = log[0].split(")")
testcases = []
for case in splitLog:
    result = parse.parse("(KService: {}, Param_pos: {}, Param_len: {}", case)
    if result != None:
        testcases.append(result)
    else:
        print("Couldn't parse '{}'".format(case))

testcases = removeDuplicates(testcases)

# case[2] holds the number of bytes the parameter has
for case in testcases:
    for i in range(0,int(case[2]) * 8):
        query = "INSERT INTO testcases (kservice, parameter, bit, module) VALUES('{}','{}','{}','{}')".format(case[0],case[1],i,targetModule)
        try:
            cursor.execute(query)
            db.commit()
        except:
            print("Executing a query failed, rolling back, Query: {}".format(query))
            db.rollback()

print("Extracting testcases finished")
cursor.close()
db.close()

