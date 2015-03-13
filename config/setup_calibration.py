#!/usr/bin/python3
#
# python script to initialize the database for experiment campaigns
#
# ATTENTION: save your results from previous campaigns,
#            this will erase your entire grinder DB!
#

import argparse
import re
import os
import pymysql
try:
    from subprocess32 import Popen, PIPE, check_call
except ImportError:
    from subprocess import Popen, PIPE, check_call

# check that the environment is properly set up
if (os.environ.get('AFI_HOME') is None):
    print("Error: AFI environment not set up properly")
    sys.exit()

# TODO: enable "unlimited" instances; skip pre-written target specs and
#       create them with the proper port specs on the fly from a template
MIN_EMU_COUNT = 1
MAX_EMU_COUNT = 32

# TODO: get DB settings from environment/properties/cmd line/...
DB_HOST = 'localhost'
DB_USER = 'grinder'
DB_PASSWORD = 'grinder'
DB_PORT = 3306
DB_NAME = 'grinder'

CAMPAIGN_MAPPER_SCRIPT = ("{db_script_dir}/generate_campaign_from_testcases.py"
                          .format(db_script_dir=os.environ.get('AFI_DBSCRIPT_DIR')))

CALIB_TARGET = "{config_dir}/android_fi_calib.xml".format(config_dir=os.environ.get('AFI_HOME')+'/config')



parser = argparse.ArgumentParser(
    description='Initialize grinder DB for campaigns.',
    formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument('--module', '-m',
                    default='goldfish_orig.ko',
                    help='Module to run the calibration with')
parser.add_argument('par_degree',
                    help='Number of parallel emulator instances as comma separated list of hyphen separated ranges')
parser.add_argument('--repetitions', '-r',
                    type=int,
                    default=10,
                    help='Number of repetitions per parallelism setup')
args = parser.parse_args()

db = pymysql.connect(host=DB_HOST,
                     port=DB_PORT,
                     user=DB_USER,
                     passwd=DB_PASSWORD,
                     db=DB_NAME,
                     autocommit=True)
cursor = db.cursor()

# clear all tables
cursor.execute("SHOW TABLES")
try:
    tables = set(cursor.fetchall())
except Error as e:
    print("Error getting table list from database {db_name}: {error}"
          .format(db_name=DB_NAME, error=e))
    sys.exit()
cursor.execute("SET FOREIGN_KEY_CHECKS=0")
for t in tables:
    cursor.execute("TRUNCATE {table}".format(table=t[0]))
cursor.execute("SET FOREIGN_KEY_CHECKS=1")

# load target spec and test case
cursor.execute("INSERT INTO targets(configuration, name) "
               "VALUES('{target_conf}', 'android_fi_calibration')"
               .format(target_conf=open(CALIB_TARGET).read()))
db.commit()
cursor.execute("INSERT INTO testcases(kservice,module,parameter,bit) "
               "VALUES('{parallelisms}','{module}',{repetitions},0)"
               .format(parallelisms=args.par_degree, 
                       module=args.module, 
                       repetitions=args.repetitions))
db.commit()
cursor.execute("SELECT id FROM testcases LIMIT 1")
testcase_id = cursor.fetchone()[0]
cursor.execute("INSERT INTO campaigns(name) VALUES('calibration')")
db.commit()
cursor.execute("SELECT id FROM campaigns LIMIT 1")
campaign_id = cursor.fetchone()[0]
cursor.execute("INSERT INTO campaigns_testcases(campaigns_id,testCases_id) "
               "VALUES('{c_id}','{t_id}')"
               .format(c_id=campaign_id,
                       t_id=testcase_id))
db.commit()
print("Fin.\n")
