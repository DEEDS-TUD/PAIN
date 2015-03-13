#!/usr/bin/env python3
# usage: Usage: python campaign_generator.py 'campaign_ID' 'campaign_name' 'result'
import  pymysql, sys

# get id of the experiment run as argument
if len(sys.argv) != 4 or not sys.argv[1].isdigit() or not sys.argv[3].isdigit():
    print("Usage: python campaign_generator.py 'campaign_ID' 'campaign_name' 'result'")
    sys.exit()
campaign_ID = sys.argv[1]
campaing_Name = sys.argv[2]
result_val = sys.argv[3]

# connect to grinder db
db = pymysql.connect(host='127.0.0.1', port=3306, user='grinder', passwd='grinder', db='grinder')
cursor = db.cursor()

# generate campaign if not already existant
cursor.execute("SELECT id FROM campaigns WHERE id = {}".format(campaign_ID))
campaign = cursor.fetchone()
if campaign == None:
    query = "INSERT INTO campaigns VALUES('{}','{}')".format(campaign_ID,campaing_Name)
    try:
        cursor.execute(query)
        db.commit()
    except Exception as e: 
        print("Executing a query failed, rolling back, Query: {}, Exception: {}".format(query,e))
        db.rollback()
        sys.exit()
   
# generate mapping between testcases and campaign
cursor.execute("SELECT testCase_id FROM `grinder`.`experiment_run` WHERE `result` = {}".format(result_val))

runs = cursor.fetchall()
for run in runs:
    query = "INSERT INTO campaigns_testcases VALUES('{}','{}')".format(campaign_ID,run[0])
    try:
        cursor.execute(query)
        db.commit()
    except Exception as e:
        print("Executing a query failed, rolling back, Query: {}, Exception: {}".format(query,e))
        db.rollback()

print("Mapping between campaign {} and testruns with result {} done".format(campaign_ID,result_val))
