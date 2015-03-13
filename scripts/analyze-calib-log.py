#!/usr/bin/env python3

import sys
import argparse
import re
import os


def handle_cmd_line():
    """TBD"""

    parser = argparse.ArgumentParser(
            description='Extract all single calibration run logs from a '
            'GRINDER calibration log file and maybe do some searching.',
            formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('logfile', help='log file to analyze')
    parser.add_argument('-s', '--search', help='Count the number of occurences of the '
            'specified regular expression. Works similar to grep, but with Python '
            'regular expressions.')
    parser.add_argument('--verbose-search', action='store_true', help='Prints search '
            'matches to the console.')
    parser.add_argument('--csvfile', default='matches.csv', help='CSV output file.')
    parser.add_argument('--clean', action='store_true', help='Delete all created split '
            'log files.')

    return parser.parse_args()
# -----

MARKER_START_RUN = re.compile(
    r'EmulatedAndroidCalib - Starting '
    r'calibration run: (\d+) parallel emus, repetition (\d+)')
MARKER_END_RUN = re.compile(r'EmulatedAndroidCalib - Finished calibration run.')
MARKER_END_ALL_RUNS = re.compile('EmulatedAndroidCalib - Finished all calibration runs.')
MARKER_EMU_LINE = re.compile(r'<(\d+)/(\d{4})> - ')
MARKER_CALIB_LINE = re.compile(r'CalibrationRun<(\d+)> - ')


def split_logfile_runs(logfile):
    """TBD"""

    print('Splitting logfile into run logfiles...')
    lf_base_path, lf_ext = os.path.splitext(logfile)
    def lf_new_name(runid):
        return '{}-{}{}'.format(lf_base_path, runid, lf_ext)

    run_logfiles = list()
    with open(logfile, 'r') as fil:
        cur_run = 0
        cur_outfil = None
        cur_state = 0
        for line in fil:
            if cur_state == 0: # outside run, initial
                match_run = MARKER_START_RUN.search(line)
                if match_run:
                    cur_run = int(match_run.group(2))
                    new_name = lf_new_name(cur_run)
                    run_logfiles.append(new_name)
                    cur_outfil = open(new_name, 'w')
                    cur_outfil.write(line)
                    cur_state = 1
            elif cur_state == 1: # within run
                match_runend = MARKER_END_RUN.search(line)
                if match_runend:
                    cur_outfil.write(line)
                    cur_state = 2
                else:
                    cur_outfil.write(line)
            elif cur_state == 2: # between runs
                match_allend = MARKER_END_ALL_RUNS.search(line)
                if match_allend:
                    cur_outfil.close()
                    break
                else:
                    match_run = MARKER_START_RUN.search(line)
                    if match_run:
                        cur_run = int(match_run.group(2))
                        cur_outfil.close()
                        new_name = lf_new_name(cur_run)
                        run_logfiles.append(new_name)
                        cur_outfil = open(new_name, 'w')
                        cur_outfil.write(line)
                        cur_state = 1
                    else:
                        cur_outfil.write(line)
    return run_logfiles
# -----

def split_logfile_emu(logfile):
    """TBD"""

    lf_base_path, lf_ext = os.path.splitext(logfile)
    def lf_new_name(emuid):
        return '{}-{}{}'.format(lf_base_path, emuid, lf_ext)

    def get_first_line_data(fil):
        line = fil.readline()
        match = MARKER_START_RUN.search(line)
        if match:
            return (int(match.group(1)), int(match.group(2)))
        else:
            raise Exception('Invalid first line of run log file: ' + line)

    def open_emu_files(count, logfiles_list):
        emu_files = list()
        cur = 0
        while cur < count:
            cur_name = lf_new_name(cur)
            logfiles_list.append(cur_name)
            emu_files.append(open(cur_name, 'w'))
            cur += 1
        return emu_files

    def close_emu_files(lst):
        for l in lst:
            l.close()

    def get_matching(line):
        match_emu = MARKER_EMU_LINE.search(line)
        if match_emu:
            return int(match_emu.group(1))
        match_calib = MARKER_CALIB_LINE.search(line)
        if match_calib:
            return int(match_calib.group(1))
        return -1

    emu_logfiles = list()
    cur_run = 0
    with open(logfile, 'r') as fil:
        num_emus, cur_run = get_first_line_data(fil)
        print('Splitting logfile for run {} into emu log files...'.format(cur_run))
        emu_files = open_emu_files(num_emus, emu_logfiles)
        for line in fil:
            emu_id = get_matching(line)
            if emu_id >= 0:
                emu_files[emu_id].write(line)
        close_emu_files(emu_files)

    return emu_logfiles
# -----

def split_logfile(logfile):
    """TBD"""

    print('Splitting logfile [{}]...'.format(logfile))
    run_logfiles = split_logfile_runs(logfile)
    emu_logfiles = [split_logfile_emu(lfil) for lfil in run_logfiles]
    print('Done splitting.')
    print('{} runs with {} emus.'.format(len(run_logfiles), len(emu_logfiles[0])))

    return (run_logfiles, emu_logfiles)
# -----

def search_emus(search_string, logfiles):
    """TBD"""

    print('Searching for [{}] in all emu logs...'.format(search_string))
    search_regex = re.compile(search_string)

    results = list()
    for run_id, run_logs in enumerate(logfiles):
        run_results = list()
        for emu_id, emu_log in enumerate(run_logs):
            emu_results = list()
            with open(emu_log, 'r') as fil:
                for n, line in enumerate(fil):
                    match = search_regex.search(line)
                    if match:
                        emu_results.append(n + 1)
            run_results.append(emu_results)
        results.append(run_results)

    return results
# -----

def output_search_results(search_results, outfile, verbose):
    """TBD"""

    print('Writing search results to [{}]...'.format(outfile))
    seen_matches = list()
    with open(outfile, 'w') as fil:
        fil.write('Run,Emu,Matches\n')
        for run_id, run_results in enumerate(search_results):
            for emu_id, emu_results in enumerate(run_results):
                match_cnt = len(emu_results)
                fil.write('{},{},{}\n'.format(run_id, emu_id, match_cnt))
                if match_cnt > 0:
                    seen_matches.append((run_id, emu_id, match_cnt))

    if verbose:
        for run_id, emu_id, cnt in seen_matches:
            print('{} matches in run {}, emu {}'.format(cnt, run_id, emu_id))
        print('Total matching emu runs: {}'.format(len(seen_matches)))
# -----

def clean_files(files):
    """TBD"""

    print('Cleaning {} files...'.format(len(files)))
    for filename in files:
        os.remove(filename)
# -----

def main():
    """TBD"""

    args = handle_cmd_line()
    run_logfiles, emu_logfiles = split_logfile(args.logfile)
    print()
    if args.search:
        search_results = search_emus(args.search, emu_logfiles)
        output_search_results(search_results, args.csvfile, args.verbose_search)
        print()
    if args.clean:
        clean_files(run_logfiles + [item for sublist in emu_logfiles for item in sublist])
        print()

    print('Fin.')
# -----


if __name__ == '__main__':
    main()

