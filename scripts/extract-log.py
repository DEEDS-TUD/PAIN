#!/usr/bin/env python3
#

import argparse
import re
import os


def handle_cmd_line():
    """TBD"""

    parser = argparse.ArgumentParser(
            description='Extract interesing portions from GRINDER log files.',
            formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('logfile', help='the log file to have a look at')
    parser.add_argument('--testcases', '-t', type=int, nargs='+',
            help='The test case IDs to have a look at.')

    return parser.parse_args()
# -----

def get_run_starts(logfile):
    """TBD"""

    print('Searching single run starts...')
    id_to_line = dict()
    last_line = 0
    max_id = 0
    with open(logfile, 'r') as fil:
        for n, l in enumerate(fil):
            match = re.search(r'Test case id: (\d+)', l)
            if match:
                cur_id = int(match.group(1))
                id_to_line[cur_id] = n
                max_id = cur_id
            last_line = n
    id_to_line[max_id + 1] = last_line # add sentinel
    print('Found {} run starts.'.format(len(id_to_line) - 1))
    return id_to_line
# -----

def extract_lines(logfile, id_to_line, tc_ids):
    """TBD"""

    def skipl(fil, num):
        while num > 0:
            next(fil)
            num -= 1
    # --
    def writel(infil, outfil, num):
        while num > 0:
            outfil.write(infil.readline())
            num -= 1
    # --

    print('Extracting log lines for {} runs...'.format(len(tc_ids)))
    logfile_split_name = os.path.splitext(logfile)
    input_ids = sorted(tc_ids)
    with open(logfile, 'r') as fil:
        cur_n = 0
        for cur_id in input_ids:
            cur_skip = id_to_line[cur_id] - cur_n
            print('Skipping {} lines...'.format(cur_skip))
            skipl(fil, cur_skip)
            cur_n += cur_skip
            outname = '{}_{}{}'.format(
                    logfile_split_name[0], cur_id, logfile_split_name[1])
            with open(outname, 'w') as outfil:
                to_write = id_to_line[cur_id + 1] - cur_n
                print('Writing {} lines starting at {} to [{}]...'.format(
                    to_write, cur_n, outname))
                writel(fil, outfil, to_write)
                cur_n += to_write

# -----

def main():
    """TBD"""

    args = handle_cmd_line()
    id_to_line = get_run_starts(args.logfile)
    print()
    extract_lines(args.logfile, id_to_line, args.testcases)
    
# -----


if __name__ == '__main__':
    main()



