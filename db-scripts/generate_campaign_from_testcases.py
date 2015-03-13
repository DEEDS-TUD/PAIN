#!/usr/bin/env python3
# usage: Usage: python campaign_generator.py 'campaign_ID' 'campaign_name'
import  pymysql, sys

# get id of the experiment run as argument
if len(sys.argv) < 4 or not sys.argv[1].isdigit():
    print("Usage: python campaign_generator.py 'campaign_ID' 'campaign_name' " +
            "'first_id' 'last_id'")
    sys.exit()
campaign_ID = sys.argv[1]
campaing_Name = sys.argv[2]
first_id = sys.argv[3]
last_id = sys.argv[4] if len(sys.argv) >= 5 else None

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
ID_QUERY = ""
if last_id is not None:
    ID_QUERY = ("SELECT id FROM testcases WHERE kservice!='testService' " +
            "and id>={} and id<={}").format(first_id, last_id)
else:
    ID_QUERY = ("SELECT id FROM testcases WHERE kservice!='testService' " +
            "and id>={}").format(first_id)
cursor.execute(ID_QUERY)
testcases = cursor.fetchall()
for case in testcases:
    query = "INSERT INTO campaigns_testcases VALUES('{}','{}')".format(campaign_ID,case[0])
    try:
        cursor.execute(query)
        db.commit()
    except Exception as e:
        print("Executing a query failed, rolling back, Query: {}, Exception: {}".format(query,e))
        db.rollback()

print("Mapping between campaign {} and {} testcases done".format(campaign_ID,
    len(testcases)))

