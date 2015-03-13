#!/usr/bin/env python3
#
# usage:

import pymysql, collections, socket, datetime
import argparse, os, io, re, sys


def handle_cmd_line():
    """Handle command line arguments from the script invocation.

    Returns
        the namespace object created by the argument parser.
    """

    parser = argparse.ArgumentParser(
                description='Read calibration data from the DB and generate GRINDER '
                'properties files with respective delay settings.',
                formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument('tcid', metavar='TESTCASE_ID',
            help='Test case ID for a calibration test case that was executed previously.')
    parser.add_argument('--single', '-s', metavar='EMU_COUNT',
            help='Produce only a single configuration file for EMU_COUNT parallel '
            'emulators if corresponding calibration data is available.')
    parser.add_argument('--dbhost', default='localhost', help='DB host')
    parser.add_argument('--dbport', default='3306', type=int, help='DB connection port')
    parser.add_argument('--dbuser', default='grinder', help='DB user namse')
    parser.add_argument('--dbpw', default='grinder', help='DB password')
    parser.add_argument('--dbname', default='grinder', help='DB name to use')
    parser.add_argument('--print-results', '-p', action='store_true',
            help='Print results to console')
    parser.add_argument('--outfile', '-o', default='grinder-afi.properties',
            metavar='FILE',
            help='Output configuration file. Used as template if calibration data for '
            'multiple emulator counts is available.')

    return parser.parse_args()
# -----

def get_raw_calib_data(cursor, dbname, test_case_id):
    """Retrieve the raw calibration data for the specified test case."""

    QRY_GET_ALL_CALIB_EXECS = '''
    SELECT id, time, campaign_id, log
    FROM {dbname}.experiment_run
    WHERE testCase_id = {tcid} and result = 1
    '''

    raw_log_string = ''
    num_calib_execs = cursor.execute(QRY_GET_ALL_CALIB_EXECS.format(dbname=dbname,
        tcid=test_case_id))
    if num_calib_execs > 1:
        print('{} executions of calibration test case {} found.'
                .format(num_calib_execs, test_case_id))
        print('Please select which execution to use')
        raw_execs = cursor.fetchall()
        print('{:>8}  {:>5}  {:>25}  {:>10}'.format('Select', 'ID', 'Time', 'Campaign'))
        for i, row in enumerate(raw_execs):
            print('{:>8}  {:>5}  {:>25}  {:>10}'.format(i, row[0], str(row[1]), row[2]))
        sel = 0
        sel_ok = False
        while not sel_ok:
            sel_str = input('--> ')
            try:
                sel = int(sel_str)
                if sel < 0 or sel > num_calib_execs - 1:
                    print('Invalid selection. Try again!')
                    continue
                sel_ok = True
            except:
                print('Invalid selection. Try again!')
                sel_str = input('--> ')
        raw_log_string = raw_execs[sel][3]
    elif num_calib_execs == 1:
        raw_log_string = cursor.fetchone()[3]
    else:
        print('ERROR: No executions of calibration test case [{}] found in '
                'DB.'.format(test_case_id))
        print('       Aborting.')
        sys.exit(1)

    return raw_log_string.splitlines()
# -----

def open_db_connection(host, port, user, passwd):
    """Open a connection to the DB server and return the connection object."""

    print('Connecting to MySQL server [{}:{}] as user [{}]...'.format(host, port, user))
    conn = pymysql.connect(host=socket.gethostbyname(host), port=port,
            user=user, passwd=passwd)
    return conn
# -----


def main():
    """Main function for direct script invocation."""

    args = handle_cmd_line()
    print(args)
    db_conn = open_db_connection(args.dbhost, args.dbport, args.dbuser, args.dbpw)
    print()
    raw_calib_data = get_raw_calib_data(db_conn.cursor(), args.dbname, args.tcid)
    print(raw_calib_data)


    print('\nFin.')


# -----

if __name__ == '__main__':
    main()


