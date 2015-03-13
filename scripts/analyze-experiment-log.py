#!/usr/bin/env python3
#
# Split a given GRINDER experiment log file into individual log files per test case.
#
# Assumptions/ on the log file:
# * The given log file may contain multiple complete GRINDER runs, i.e., the same log file
#   has been used for multiple GRINDER runs.
#   -> In this case the multi-run log file is split and one log file per run is generated.
#      The run ID is appended to the original log file name
#   -> Use the '--single-run' option if you are sure that only one run is contained in the
#      log. This speeds up processing
# * The given log file may contain experiment runs with multiple parallel emulators. The
#   emulator IDs must be consecutive and start with 0, e.g., for 4 emus, the IDs are 0, 1,
#   2, 3.
#   -> For multi-emu runs, the log file is split and one log file per emulator is
#      generated.
#   -> Use the '--single-emu' option if you are sure that only one emulator was used for
#      the experiments, i.e., the experiments are sequential.
# * The given log file usually contains multiple test cases. Test cases have IDs that are
#   consecutive and start with 1, e.g., for 200 test cases IDs are 1..200. The IDs must be
#   unique within a GRINDER run, i.e., there are no two emulators that are use with the
#   same test case. The mapping of test cases to emulators must follow the same scheme as
#   the experiment setup script: emu_id = (tc_id - 1) // (testcase_count // emu_count).
#   In other words, every emulator gets the same amount of work, except for the last one,
#   which may indeed have to cope with almost the double amount of work compared to the
#   others.
#   -> The log file is split and one log file per test case is generated.
#   -> For the mapping test case ID to emu ID, the script needs to now the number of
#      emulators and test cases. You can specify these numbers via the '--emus' and
#      '--testcases' options or the script computes them with an additional scan pass over
#      the log file. Using the command line options saves time.
#
# Advice:
#  Keep an eye on the results if you use the various command line options since they are
#  not checked for consistency with the log data. For instance, if you specify a wrong
#  test case count, the results will be wrong without the script noticing.
#
# Example invocation for single run log, sequential experiments, 400 testcases:
#   analyze-experiment-log.py --single-run --single-emu --testcases 400 --clean \
#       --times-out log1-times.csv log1.log
#


import sys
import argparse
import re
import os
import textwrap
import datetime as dt

# log entry markers
#-----------------------------------------------------
MARKER_GRINDER_START = re.compile('Starting the GRINDER client')
MARKER_GRINDER_END = re.compile('IOException while receiving message: '
        'Reached end of stream.')
MARKER_EMU_START = re.compile(r'EmulatedAndroid<(\d+)/(\d+)> - Starting emulated Android')
MARKER_EXP_FINISH = re.compile(r'Detected experiment end with result: ([A-Z_]+)')
MARKER_EMU_LINE = re.compile(r'<(\d+)/(\d+)> - ')
MARKER_TESTCASE_LINE = re.compile(r'TargetControllerImpl - Test case id: (\d+)')

TIMEMARK_EXP_START = re.compile(
        r'^(.+_.+) [A-Z]+ .+EmulatedAndroid<.+> - Starting experiment run')
TIMEMARK_WL_START = re.compile(
        r'^(.+_.+) [A-Z]+ .+EmulatedAndroid<.+> - Signaling Detector')
TIMEMARK_EXP_DETECT = re.compile(
        r'^(.+_.+) [A-Z]+ .+ExternalDetector<.+> - '
        r'Detected experiment end with result: ([A-Z_]+)')
TIMEMARK_TIME_FORMAT = '%Y-%m-%d_%H:%M:%S.%f'
GOOD_EXP_RESULTS = {'FINISHED'}
#-----------------------------------------------------


def handle_cmd_line():
    """TBD"""

    parser = argparse.ArgumentParser(
            description='Extract all single emulator logs from a GRINDER experiment log '
            'file and do some analyses. Currently, experiment times are extracted and '
            'written to a CSV file. The specified log file may contain '
            'multiple GRINDER runs. Note that partial logs are not explicitly supported; '
            'so, watch out if attempting to analyze partial logs! '
            'Only log files with consecutive emulator and test case IDs, with 0 as first '
            'emulator ID and 1 as first test case ID, are supported! Test case IDs must '
            'be distributed across emulator IDs according to the scheme of the '
            'experiment setup script. This script is likely to silently produces wrong '
            'data on input log files that do not meet the stated assumptions',
            formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('logfile', help='The log file to analyze. May contain multiple '
            'complete GRINDER runs.')
    parser.add_argument('--single-run', action='store_true',
            help='Assume that the given logfile only contains one GRINDER run. '
            'In this case, no attempt is made to split the given logfile into individual '
            'files for each contained GRINDER run.')
    parser.add_argument('--single-emu', action='store_true',
            help='Assume that the given log file only contains one emulator. '
            'In this case, no attempt is made to split the given logfile into individual '
            'files for each emulator. Note that this option is not the same as '
            'specifying the --emus option with a value of 1. In fact, --emus cannot be '
            'specified together with this option.')
    parser.add_argument('--testcases', '-t', type=int,
            help='Specify the number of testcases in a single run log. Only valid in '
            'combination with the --emus option. If specified, the first log analysis '
            'pass is skipped. Note that no individual numbers can be specified for run '
            'in multi-run log files.')
    parser.add_argument('--emus', '-e', type=int,
            help='Specify the number of emulator instances in a single run log. Only '
            'valid in combination with the --testcases option. If specified, the first '
            'log analysis pass is skipped. Note that no individual numbers can be '
            'specified for runs in multi-run log files.')
    parser.add_argument('--times-out', default='times.csv',
            help='Specify the CSV output file for the experiment time analysis.')
    parser.add_argument('--clean', action='store_true', help='Delete all created split '
            'log files.')

    args = parser.parse_args()

    # sanity checks
    if args.single_emu:
        if args.emus is not None:
            print("error: options '--single-emu' and '--emus' cannot both be specified.")
            sys.exit(2)
        elif args.testcases is not None:
            args.emus = 1
    if (args.emus is None) != (args.testcases is None):
        print("error: options '--emus' and '--testcases' must both be specified.")
        sys.exit(2)

    # additional infos for users
    if not args.single_run:
        print(textwrap.fill(
            "INFO: You may want to use the '--single-run' option if the logfile "
            "contains only a single GRINDER run. This skips the splitting into "
            "individual log files per run, which saves processing time.", width=80))
        print()
    if not args.single_emu:
        print(textwrap.fill(
            "INFO: You may want to use the '--single-emu' option if the logfile "
            "contains only a single emulator. This skips the splitting into "
            "individual log files per emulator, which saves processing time.", width=80))
        print()
    if args.testcases is None and not args.single_emu:
        print(textwrap.fill(
            "INFO: You may want to use the '--emus' and '--testcases' options if you "
            "know the exact number of emulators and test cases that are contained in "
            "the logs. Note that you cannot use these options if you have a mulit-run "
            "log file with different emulator or testcase numbers.", width=80))
        print()
    if not args.clean:
        print(textwrap.fill(
            "INFO: You may want to use the '--clean' option to delete all created split "
            "log files after the analysis is done with them to save space."))
        print()

    return args
# -----


def get_logfile_name_fn(logfile, pattern):
    """TBD"""

    base, ext = os.path.splitext(logfile)
    def new_name(ins):
        nonlocal base, ext
        return pattern.format(base=base, ext=ext, ins=ins)
    return new_name
# -----

def split_logfile_runs(logfile):
    """TBD"""

    print('Splitting logfile [{}] into run logfiles...'.format(logfile))
    lf_new_name = get_logfile_name_fn(logfile, '{base}-{ins}{ext}')

    run_logfiles = list()
    def start_new_run(cur_run):
        nonlocal run_logfiles
        new_name = lf_new_name(cur_run)
        run_logfiles.append(new_name)
        cur_outfil = open(new_name, 'w')
        return cur_outfil
    # --
    with open(logfile, 'r') as fil:
        cur_run = -1
        cur_outfil = None
        cur_state = 0
        for line in fil:
            if cur_state == 0: # outside run, initial
                match_run = MARKER_GRINDER_START.search(line)
                if match_run:
                    cur_run += 1
                    cur_outfil = start_new_run(cur_run)
                    cur_outfil.write(line)
                    cur_state = 1
            elif cur_state == 1: # within run
                match_nextrun = MARKER_GRINDER_START.search(line)
                if match_nextrun:
                    cur_outfil.close()
                    cur_run += 1
                    cur_outfil = start_new_run(cur_run)
                    cur_outfil.write(line)
                else:
                    cur_outfil.write(line)
        if cur_outfil:
            cur_outfil.close()

    print('Found {} GRINDER runs in logfile.'.format(len(run_logfiles)))
    return run_logfiles
# -----

def split_logfiles_emus(run_logfiles, emu_count, testcase_count):
    """TBD"""

    print('Splitting {} run logfiles into emu logfiles...'.format(len(run_logfiles)))

    emu_logfiles = list()
    for run_log in run_logfiles:
        emu_logfiles.append(split_logfile_emus(run_log, emu_count, testcase_count))

    print('Finished splitting of {} run log files into {} emu log files.'
            .format(len(run_logfiles), sum(len(x) for x in emu_logfiles)))
    return emu_logfiles
# -----

def split_logfile_emus(logfile, emu_count, testcase_count):
    """TBD"""

    print('Splitting [{}] into emu logfiles...'.format(logfile))
    lf_new_name = get_logfile_name_fn(logfile, '{base}-{ins}{ext}')

    if emu_count is None:
        emu_count, testcase_count = get_emu_tc_count(logfile)
    else:
        print('Using user supplied emu and test case counts: {}, {}'
                .format(emu_count, testcase_count))

    print('Splitting in progress...')

    tc_step = testcase_count // emu_count
    emu_names = dict()
    emu_files = dict()
    seen_emus = set()
    seen_testcases = dict()

    def add_tc(tc_id):
        if tc_id not in seen_testcases:
            seen_testcases[tc_id] = 0
        seen_testcases[tc_id] += 1

    def add_emu(emu_id):
        seen_emus.add(emu_id)

    def ensure_emu(emu_id):
        if not emu_id in emu_names:
            cur_name = lf_new_name(emu_id)
            emu_names[emu_id] = cur_name
            emu_files[emu_id] = open(cur_name, 'w')

    def close_emu_files():
        for fil in emu_files.values():
            fil.close()
        emu_files.clear()

    def emu_for_tc_id(tc_id):
        emu_id =  (tc_id - 1) // tc_step
        if emu_id > emu_count - 1:
            emu_id = emu_count - 1
        return emu_id

    def get_matching(line):
        match_emu = MARKER_EMU_LINE.search(line)
        if match_emu:
            emu_id = int(match_emu.group(1))
            add_emu(emu_id)
            return emu_id
        match_tc = MARKER_TESTCASE_LINE.search(line)
        if match_tc:
            tc_id = int(match_tc.group(1))
            add_tc(tc_id)
            return emu_for_tc_id(tc_id)
        return -1

    with open(logfile, 'r') as fil:
        for line in fil:
            emu_id = get_matching(line)
            if emu_id >= 0:
                ensure_emu(emu_id)
                emu_files[emu_id].write(line)
        close_emu_files()

    print('Finished splitting [{}] into emu logfiles.'.format(logfile))
    print('Checking for consistency...')
    results_ok = True
    if emu_count != len(seen_emus):
        print('ERROR: Expected to see {} emus, but saw {}.'.format(emu_count,
            len(seen_emus)))
        results_ok = False
    if len(seen_emus) != len(emu_names):
        print('ERROR: Saw {} emus, but collected {}.'.format(len(seen_emus),
            len(emu_names)))
        print('seen emus: {}'.format(', '.join(str(nam) for nam in seen_emus)))
        print('collected emus: {}'.format(', '.join(str(nam) for nam in emu_names.keys())))
        results_ok = False
    if testcase_count != len(seen_testcases):
        print('ERROR: Expected to see {} test cases, but saw {}.'.format(testcase_count,
            len(seen_testcases)))
        results_ok = False
    if (not check_number_range(sorted(seen_emus), 0, 'Emulator IDs...') or
        not check_number_range(sorted(seen_testcases.keys()), 1, 'Test case IDs...')):
        results_ok = False
    tc_doubles = sorted(x[0] for x in seen_testcases.items() if x[1] > 1)
    if len(tc_doubles) > 0:
        print('ERROR: Saw test cases more than once: {}'.format(', '.join(str(tc_doubles))))
        results_ok = False

    if results_ok:
        print('Found no consistency issues with log split.')
    else:
        print('ERROR: Found consistency issues! Please check manually!')
        resp = input('Continue anyway? [y/N] ')
        if resp != 'y':
            sys.exit(0)

    return [y[1] for y in sorted(emu_names.items(), key=lambda x: x[0])]
# -----


def check_number_range(numbers, start, msg):
    """TBD"""

    print(msg, end=' ')
    i = start
    for num in numbers:
        if num != i:
            print('FAIL.')
            print('Inconsistency in number range. Expected {}, but got {}.'
                    .format(i, num))
            return False
        i += 1
    print('OK.')
    return True
# -----

def get_emu_tc_count(logfile):
    """TBD"""

    print('Analyzing emulator and testcase counts...')

    seen_testcases = set()
    seen_emus = set()
    with open(logfile, 'r') as fil:
        for line in fil:
            match_tc = MARKER_TESTCASE_LINE.search(line)
            if match_tc:
                tc_id = int(match_tc.group(1))
                if tc_id in seen_testcases:
                    print('WARNING: Duplicate test case ID: {}'.format(tc_id))
                seen_testcases.add(tc_id)
                continue
            match_emu = MARKER_EMU_START.search(line)
            if match_emu:
                emu_id = int(match_emu.group(1))
                seen_emus.add(emu_id)

    print('Found {} emus and {} testcases.'.format(len(seen_emus), len(seen_testcases)))
    print('Checking consistency ...')

    seen_emus = sorted(seen_emus)
    seen_testcases = sorted(seen_testcases)


    emus_ok = check_number_range(seen_emus, 0, 'Emulator IDs...')
    tc_ok = check_number_range(seen_testcases, 1, 'Test case IDs...')
    if not emus_ok or not tc_ok:
        print('ERROR')
        print('Log appears to be incomplete or has gaps in emu or test case IDs.')
        print('This is not supported.')
        raise Exception('Cannot continue: emu or test cas ID issue.')
    else:
        print('Max IDs: emu: {}  test case: {}'
                .format(seen_emus[-1], seen_testcases[-1]))

    return (len(seen_emus), len(seen_testcases))
# -----

def split_logfiles_testcases(emu_logfiles):
    """TBD"""

    print('Splitting emu logfiles into testcase logfiles...')

    tc_logfiles = list()
    for run_logs in emu_logfiles:
        run_tc_logfiles = list()
        for emu_log in run_logs:
            run_tc_logfiles.append(split_logfile_testcases(emu_log))
        tc_logfiles.append(run_tc_logfiles)

    print('Finished splitting of {} emu log files into {} test case log files.'
            .format(sum(len(x) for x in tc_logfiles),
                sum(len(x) for sub in tc_logfiles for x in sub)))
    return tc_logfiles
# -----

def split_logfile_testcases(logfile):
    """TBD"""

    print('Splitting [{}] into testcase logfiles...'.format(logfile))
    lf_new_name = get_logfile_name_fn(logfile, '{base}-{ins}{ext}')

    tc_names = dict()
    cur_tc_file = None
    with open(logfile, 'r') as fil:
        cur_state = 0
        for line in fil:
            if cur_state == 0:
                match_tc = MARKER_TESTCASE_LINE.search(line)
                if match_tc:
                    cur_tc_id = int(match_tc.group(1))
                    cur_tc_name = lf_new_name(cur_tc_id)
                    tc_names[cur_tc_id] = cur_tc_name
                    cur_tc_file = open(cur_tc_name, 'w')
                    cur_tc_file.write(line)
                    cur_state = 1
            elif cur_state == 1:
                match_next = MARKER_TESTCASE_LINE.search(line)
                if match_next:
                    cur_tc_id = int(match_next.group(1))
                    cur_tc_name = lf_new_name(cur_tc_id)
                    tc_names[cur_tc_id] = cur_tc_name
                    cur_tc_file.close()
                    cur_tc_file = open(cur_tc_name, 'w')
                    cur_tc_file.write(line)
                else:
                    cur_tc_file.write(line)
        cur_tc_file.close()

    return tc_names
# -----

def get_experiment_times(tc_logfiles):
    """TBD"""

    print('Extracting experiment times for test cases...')

    run_times = list()
    for run_id, run_logs in enumerate(tc_logfiles):
        emu_times = list()
        for emu_id, emu_logs in enumerate(run_logs):
            tc_times = dict()
            for tc_id, tc_log in emu_logs.items():
                cur_times = get_tc_exp_times(tc_log)
                if not cur_times:
                    print('ERROR: For test case {}, emu: {}, run: {}'
                            .format(tc_id, emu_id, run_id))
                    print('Please check & fix manually!')
                    sys.exit(1)
                else:
                    tc_times[tc_id] = cur_times
            emu_times.append(tc_times)
        run_times.append(emu_times)

    return run_times
# -----

def get_tc_exp_times(logfile):
    """TBD"""

    init_start = None
    wl_start = None
    exp_detect = None
    exp_class = None
    with open(logfile, 'r') as fil:
        cur_state = 0
        for line in fil:
            if cur_state == 0: # before start
                match_instart = TIMEMARK_EXP_START.search(line)
                if match_instart:
                    init_start = match_instart.group(1)
                    cur_state = 1
            elif cur_state == 1: # after start, searching wl start or crash
                match_wlstart = TIMEMARK_WL_START.search(line)
                if match_wlstart:
                    wl_start = match_wlstart.group(1)
                    cur_state = 2
                else:
                    match_efin = TIMEMARK_EXP_DETECT.search(line)
                    if match_efin:
                        exp_detect = match_efin.group(1)
                        exp_class = match_efin.group(2)
                        break
            elif cur_state == 2: # wl started, searching for detection
                match_efin = TIMEMARK_EXP_DETECT.search(line)
                if match_efin:
                    exp_detect = match_efin.group(1)
                    exp_class = match_efin.group(2)
                    break

    # sanity check
    if not init_start or not exp_detect:
        print('ERROR: Did not find experiment start and/or end ({} -> {}).'
                .format(init_start, exp_detect))
        return None
    # we only get meaningful times if not crashed during sys init
    if wl_start:
        start_time = dt.datetime.strptime(init_start, TIMEMARK_TIME_FORMAT)
        wl_time = dt.datetime.strptime(wl_start, TIMEMARK_TIME_FORMAT)
        end_time = dt.datetime.strptime(exp_detect, TIMEMARK_TIME_FORMAT)
        sysinit_millis = int(round((wl_time - start_time).total_seconds() * 1000.0))
        wl_millis = int(round((end_time - wl_time).total_seconds() * 1000.0))
        if exp_class not in GOOD_EXP_RESULTS: # wl time only sometimes meaningful
            wl_millis = -1
        return (sysinit_millis, wl_millis)
    else: # no meaningful time data
        return (-1, -1)
# -----

def write_testcase_times(tc_times, filename):
    """TBD"""

    print('Writing test case times to CSV file [{}]...'.format(filename))
    with open(filename, 'w') as fil:
        fil.write('Run,Emu,Testcase,SysInit,Workload\n')
        for run_id, run_times in enumerate(tc_times):
            for emu_id, emu_times in enumerate(run_times):
                for tc_id, cur_times in sorted(emu_times.items(), key=lambda x: x[0]):
                    fil.write('{},{},{},{},{}\n'
                            .format(run_id, emu_id, tc_id, cur_times[0], cur_times[1]))
    print('Finished writing CSV file.')
# -----

def flatten(nested_list):
    """TBD"""

    def flatten_gen(nst_lst):
        for item in nst_lst:
            if isinstance(item, (tuple, list)):
                for subitem in flatten_gen(item):
                    yield subitem
            else:
                yield item
    return list(flatten_gen(nested_list))
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

    # split the logfile into runs, emus and testcases, the result is a list of lists of
    # dicts: [[dict()]]
    run_logfiles = [args.logfile]
    if args.single_run:
        print('Skipping GRINDER run splitting (single run option was specified).')
    else:
        run_logfiles = split_logfile_runs(args.logfile)
    print()

    emu_logfiles = [run_logfiles]
    if args.single_emu:
        print('Skipping per emulator splitting (single emu option was specified).')
    else:
        emu_logfiles = split_logfiles_emus(run_logfiles, args.emus, args.testcases)
    print()

    tc_logfiles = split_logfiles_testcases(emu_logfiles)
    print()

    # here the analysis part starts based on the single test case log files
    tc_times = get_experiment_times(tc_logfiles)
    print()
    write_testcase_times(tc_times, args.times_out)
    print()

    # do cleanup if requested
    if args.clean:
        files = list()
        if not args.single_run:
            files.extend(run_logfiles)
        if not args.single_emu:
            files.extend(flatten(emu_logfiles))
        files.extend(flatten(list(dic.values()) for dic in flatten(tc_logfiles)))
        clean_files(files)
        print()

    print('Fin.')
# -----


if __name__ == '__main__':
    main()

