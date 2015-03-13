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
import sys
import pymysql
try:
    from subprocess32 import Popen, PIPE, check_call, CalledProcessError
except ImportError:
    from subprocess import Popen, PIPE, check_call, CalledProcessError

# TODO: enable "unlimited" instances; skip pre-written target specs and
#       create them with the proper port specs on the fly from a template
MIN_EMU_COUNT = 1
MAX_EMU_COUNT = 48

# TODO: get DB settings from environment/properties/cmd line/...
DB_HOST = 'localhost'
DB_USER = 'grinder'
DB_PASSWORD = 'grinder'
DB_PORT = 3306
DB_NAME = 'grinder'

CAMPAIGN_MAPPER_SCRIPT = ("{db_script_dir}/generate_campaign_from_testcases.py"
                          .format(db_script_dir=os.environ.get('AFI_DBSCRIPT_DIR')))

parser = argparse.ArgumentParser(
    description='Initialize grinder DB for campaigns.',
    formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument('tc_table',
                    help='SQL file with the testcase table')
parser.add_argument('par_degree',
                    type=int,
                    choices=range(MIN_EMU_COUNT, MAX_EMU_COUNT + 1),
                    help='Number of parallel emulator instances')
parser.add_argument('cname',
                    help='Campaign name template')
args = parser.parse_args()

# check that the environment is properly set up
if (os.environ.get('AFI_HOME') is None):
    print("Error: AFI environment not set up properly")
    sys.exit()

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

# load testcase table
try:
    inputfile = open("{filename}".format(filename=args.tc_table))
    check_call(["mysql",
                "-u",
                DB_USER,
                "-p"+DB_PASSWORD,
                DB_NAME],
               stdin=inputfile)
except IOError as ioe:
    print("IO error opening file {filename}: {error}"
          .format(filename=args.tc_table, error=ioe))
except CalledProcessError as cpe:
    print("Failed to execute MySQL client: {error}".format(error=cpe))

# get number of testcases to process
cursor.execute("SELECT COUNT(*) FROM testcases")
tc_count = cursor.fetchone()[0]
cursor.execute("SELECT id FROM testcases ORDER BY id LIMIT 1")
offset = cursor.fetchone()[0] - 1

# load target specs and create campaigns
for i in range(1, args.par_degree + 1):
    t_name = "android_fi{tnum:02}.xml".format(tnum=i)
    t_file_name = ("{grinder_android}/target-specs/"
                   .format(grinder_android=os.environ.get('AFI_GRINDER_ANDROID_DIR'))
                   + t_name)
    first_tc = (i-1)*(tc_count//args.par_degree)+1+offset
    last_tc = i*(tc_count//args.par_degree)+offset
    if i == args.par_degree:
        last_tc = tc_count+offset
    cursor.execute("INSERT INTO targets(configuration, name) "
                   "VALUES('{target_conf}', '{target_name}')"
                   .format(target_conf=open(t_file_name).read(),
                           target_name=t_name))
    db.commit()
    try:
        check_call([CAMPAIGN_MAPPER_SCRIPT,
                    "{campaign_id}".format(campaign_id=i),
                    "{campaign_name}"
                    .format(campaign_name=args.cname
                            + "{seqnr:02}"
                            .format(seqnr=i)),
                    "{first_id}".format(first_id=first_tc),
                    "{last_id}".format(last_id=last_tc)])
    except CalledProcessError as cpe:
        print("Failed to execute campaign mapper script: {error}"
              .format(error=cpe))
