#!/usr/bin/env python3
#
# usage:

import pymysql, collections, socket, datetime
import argparse, os, io, re, csv
import numpy

EXPERIMENT_RESULT_NAMES = {
        -1: 'EXPERIMENT_FAILURE'.lower(),
        0: 'NONE'.lower(),
        1: 'FINISHED'.lower(),
        2: 'SYSTEM_HANG_DETECTED'.lower(),
        3: 'SYSTEM_HANG_ASSUMED'.lower(),
        4: 'SYSTEM_CRASH_DETECTED'.lower(),
        5: 'APPLICATION_HANG_DETECTED'.lower(),
        6: 'APPLICATION_HANG_ASSUMED'.lower(),
        7: 'APPLICATION_FAULT_DETECTED'.lower(),
        8: 'SYSINIT_HANG_ASSUMED'.lower(),
        9: 'SYSTEM_OOPS_DETECTED'.lower()
}

class AnaResult(collections.UserDict):

    def __init__(self, **kwargs):
        tmp = dict((k, 0) for k in EXPERIMENT_RESULT_NAMES.values())
        tmp['db'] = ''
        tmp['run_id'] = 0
        tmp['first_id'] = -1
        tmp['last_id'] = -1
        tmp['start_time'] = ''
        tmp['end_time'] = ''
        tmp['total_time'] = 0
        tmp['avg_time'] = 0
        tmp['real_total_time'] = 0
        tmp['real_avg_time'] = 0
        super(AnaResult, self).__init__(tmp, **kwargs)
        acc_data = None
    # -----

    def __str__(self):
        return '\n'.join('{:30}: {:5}'.format(k, self.data[k]) for k in self.csv_order())
    # -----

    def stats(self):
        out = io.StringIO()
        out.write('{:30} : {}\n'.format('database', self.data['db']))
        out.write('{:30} : {} (runs {} - {})\n'.format('Run ID',
            self.data['run_id'], self.data['first_id'], self.data['last_id']))
        total = self.num_runs()
        for k in self.csv_order_num():
            out.write('{:30} : {:5} ({:6.2%})\n'
                    .format(k, self.data[k], self.data[k] / total))
        out.write('---\ntotal runs: {}'.format(total))
    # -----

        return out.getvalue()
    # -----

    @staticmethod
    def csv_order_num():
        return ([nam for (_, nam) in
                sorted(EXPERIMENT_RESULT_NAMES.items(), key=lambda x: x[0])] +
                ['total_time', 'avg_time', 'real_total_time', 'real_avg_time'])
    # -----

    @staticmethod
    def csv_order():
        return ['db', 'run_id'] + AnaResult.csv_order_num()
    # -----

    def num_runs(self):
        return sum(self.data[k] for k in self.csv_order_num())
    # -----

    def str_dict(self):
        return dict((k, v)
                if not isinstance(v, float)
                else (k, '{:.0f}'.format(v))
                for k, v in self.data.items())
    # -----

# -----

class AnaResultStats(collections.UserDict):

    def __init__(self, **kwargs):
        tmp = dict(('mean_' + k, 0) for k in EXPERIMENT_RESULT_NAMES.values())
        tmp.update(('stdev_' + k, 0) for k in EXPERIMENT_RESULT_NAMES.values())
        tmp['group'] = ''
        super(AnaResultStats, self).__init__(tmp, **kwargs)
    # -----

    def add_with_prefix(self, fields, prefix):
        for nam, val in fields.items():
            self.data[prefix + nam] = val
    # -----

    def add_means(self, fields):
        self.add_with_prefix(fields, 'mean_')
    # -----

    def add_stdevs(self, fields):
        self.add_with_prefix(fields, 'stdev_')
    # -----

    def str_dict(self):
        return dict((k, v)
                if not isinstance(v, float)
                else (k, '{:.2f}'.format(v))
                for k, v in self.data.items())
    # -----


    @staticmethod
    def csv_order_num():
        fields = AnaResult.csv_order_num()
        tmp = [('mean_' + n, 'stdev_' + n) for n in fields]
        return [val for pairs in tmp for val in pairs]
    # -----

    @staticmethod
    def csv_order():
        return ['group'] + AnaResultStats.csv_order_num()
    # -----

# -----

class AccData(collections.UserDict):

    def __init__(self, **kwargs):
        super(AccData, self).__init__({'db': ''}, **kwargs)

    def csv_order(self):
        known_names = ['db']
        def keyorder(nam):
            if nam.startswith('mean_'):
                return nam[len('mean_'):] + '_mean'
            elif nam.startswith('stdev_'):
                return nam[len('stdev_'):] + '_stdev'
            else:
                return nam
        # --

        filtered_keys = (k for k in self.data.keys() if not k.endswith('_count'))
        return known_names + sorted(
                (k for k in filtered_keys if k not in known_names), key=keyorder)
    # -----

    def add_with_prefix(self, fields, prefix):
        for nam, val in fields.items():
            self.data[prefix + nam] = val
    # -----

    def add_means(self, fields):
        self.add_with_prefix(fields, 'mean_')
    # -----

    def add_stdevs(self, fields):
        self.add_with_prefix(fields, 'stdev_')
    # -----

    def str_dict(self):
        filtered_data = filter(lambda x: not x[0].endswith('_count'), self.data.items())
        return dict((k, v)
                if not isinstance(v, float)
                else (k, '{:.2f}'.format(v))
                for k, v in filtered_data)
    # -----

# -----

def handle_cmd_line():
    """Handle command line arguments from the script invocation.

    Returns
        the namespace object created by the argument parser.
    """

    parser = argparse.ArgumentParser(
                description='Analyze GRINDER DB and write results to file.',
                formatter_class=argparse.ArgumentDefaultsHelpFormatter)


    parser.add_argument('--dbhost', default='localhost', help='DB host')
    parser.add_argument('--dbport', default='3306', type=int, help='DB connection port')
    parser.add_argument('--dbuser', default='grinder', help='DB user namse')
    parser.add_argument('--dbpw', default='grinder', help='DB password')
    parser.add_argument('--dbnames', default=['grinder'], nargs='+',
            help='Names of databases to access for analysis. These are regular '
            'expressions in Python syntax. Name patterns must match the whole DB name.')
    parser.add_argument('--run-groups', default='', nargs='+',
            help='Regular expressions in Python syntax that identify run groups that '
            'span multiple databases. One expression identifies one run group. The '
            'expression must match the complete DB name. Multiple runs in one DB are '
            'always assumed to belong to the same run group.')
    parser.add_argument('--print-results', '-p', action='store_true',
            help='Print results to console')
#    parser.add_argument('--campaigns', type=int, nargs='+', metavar='ID',
#            help='The list of campaign IDs to analyze.')
    parser.add_argument('--outfile', '-o', default='results.csv', metavar='FILE',
            help='Output file for the results.')
    parser.add_argument('--resetfile', action='store_true', help='Reset the output file '
            'if already existing.')

    return parser.parse_args()
# -----

def select_db_names(cursor, name_patterns):
    """Get concrete names of databases from regular expressions list."""

    print('Selecting databases for analysis...')
    cursor.execute('show databases')
    dbnames = [nam[0]
            for nam in cursor.fetchall()
            for pat in name_patterns
            if re.match(r'^' + pat + r'$', nam[0])]
    dbnames.sort()
    print('Selected {} databases:\n{}'.format(len(dbnames), '\n'.join(dbnames)))
    return dbnames
# -----

def analyze_experiment_runs(cursor, dbnames):
    """Analyze each identified experiment run and return the results"""

    print('Analyzing experiment runs...')
    result_list = list()
    for dbnam in dbnames:
        campaign_set_runs = count_campaign_set_runs(cursor, dbnam)
        for (run_id, (first_id, last_id)) in enumerate(campaign_set_runs):
            print('Processing {}<{}>, {} - {}'.format(dbnam, run_id, first_id, last_id))
            cur_result = AnaResult(db=dbnam, run_id=run_id, first_id=first_id,
                    last_id=last_id)
            get_result_distrib(cursor, dbnam, cur_result)
            get_run_time(cursor, dbnam, cur_result)
            get_accounting_data(cursor, dbnam, dbnames, cur_result)
            result_list.append(cur_result)
    print('Collected data for {} experiment runs'.format(len(result_list)))
    return result_list
# -----

def get_result_distrib(cursor, dbname, result):
    """Get the distribution of experiment results for one run."""

    QRY_RESULT_DISTRIB = '''
        SELECT result, COUNT(id)
        FROM {dbname}.experiment_run
        WHERE id >= {first_id} and id <= {last_id}
        GROUP BY result
    '''

    first_id = result['first_id']
    last_id = result['last_id']
    cursor.execute(QRY_RESULT_DISTRIB
            .format(dbname=dbname, first_id=first_id, last_id=last_id))
    for (result_id, cnt) in cursor.fetchall():
        result[EXPERIMENT_RESULT_NAMES[result_id]] = cnt
    # please close your eyes or look somewhere else. ugly hack follows...
    result[EXPERIMENT_RESULT_NAMES[4]] += result[EXPERIMENT_RESULT_NAMES[9]]
    result[EXPERIMENT_RESULT_NAMES[9]] = 0
    return result
# -----

def get_accounting_data(cursor, dbname, all_dbnames, result):
    """Retrieve the accounting data for the experiment run and integrate it with the
    existing result data.
    """

    def values(lst, key):
        return [l[key] for l in lst]
    def calc(fnc, dics, keys):
        result = dict()
        for k in keys:
            result[k] = fnc(values(dics, k))
        return result

    raw_logs = get_raw_log_data(cursor, dbname, all_dbnames, result)
    logs = parse_raw_logs(raw_logs)
    keys = logs[0].keys()
    means = calc(numpy.mean, logs, keys)
#    stdevs = calc(numpy.std, logs, keys)

    acc_data = AccData(db=dbname)
    acc_data.add_means(means)
#    acc_data.add_stdevs(stdevs)

    result.acc_data = acc_data
# -----

def parse_raw_logs(raw_logs):
    """Convert a list of raw logs into a list of dictionaries"""

    ACC_DATASET_PATTERN = '\\(((\\w+: \\d+;)+)\\)'
    ACC_DATAPOINT_PATTERN = '(\\w+): (\\d+);'

    def parse(rlog):
        final_data = dict()
        for lin in rlog.split('\n'):
            data = re.match(ACC_DATASET_PATTERN, lin).group(1)
            data_points = re.findall(ACC_DATAPOINT_PATTERN, data)
            final_data.update((k, int(v)) for k, v in data_points)
        return final_data
    # --

    return [parse(l) for l in raw_logs]
# -----

def get_raw_log_data(cursor, dbname, all_dbnames, result):
    """Retrieve the log entries for the given experiment run.

    Test cases which have a missing log in any of the experiment runs are filtered out.

    Returns
        a list of all raw log entries
    """

    QRY_TESTCASES_WO_LOG = '''
    SELECT testCase_id
    FROM {dbname}.experiment_run
    WHERE log like ''
    '''

    QRY_LOGS_FILTERED = '''
    SELECT
        log
    FROM
        {dbname}.experiment_run
    WHERE
        testCase_id not in (
            SELECT
                testCase_id
            FROM (
                {nolog_union_subquery}
            ) ALLGRINDER
            GROUP BY testCase_id
        )
        AND id >= {first_id} AND id <= {last_id}
    '''

    def build_nolog_subquery():
        out = io.StringIO()
        out.write(QRY_TESTCASES_WO_LOG.format(dbname=all_dbnames[0]))
        for db in all_dbnames[1:]:
            out.write('UNION ALL')
            out.write(QRY_TESTCASES_WO_LOG.format(dbname=db))
        return out.getvalue()
    # --

    cursor.execute(QRY_LOGS_FILTERED
            .format(dbname=dbname, nolog_union_subquery=build_nolog_subquery(),
                first_id=result['first_id'], last_id=result['last_id']))
    return [r[0] for r in cursor.fetchall()]
# ----

def get_run_time(cursor, dbname, result):
    """Retrieve runtime data for the experiments runs and integrate it with the existing
    result data.
    """

    QRY_MINMAX_TIME='''
    SELECT
        E0.id, 
        E0.time,
        E0.endtime
    FROM
        (SELECT
            id,
            time,
            TIMESTAMPADD(MICROSECOND, executionTime*1000, time) as endTime
        FROM
            {dbname}.experiment_run
        ) E0
    INNER JOIN
        (SELECT 
            MAX(endtime) as max_endtime,
            MIN(time) as min_starttime
        FROM 
            (SELECT
                id,
                time,
                TIMESTAMPADD(MICROSECOND, executionTime*1000, time) as endTime
            FROM
                {dbname}.experiment_run
            ) EE
        WHERE id >= {first_id} and id <= {last_id}
        ) E1
        ON E1.max_endtime = E0.endtime OR E1.min_starttime = E0.time
    GROUP BY E0.time
    ORDER BY E0.time ASC
    '''

    QRY_REAL_AVG_EXEC_TIME = '''
    SELECT AVG(executionTime), SUM(executionTime)
    FROM {dbname}.experiment_run
    WHERE id >= {first_id} and id <= {last_id}
    '''

    first_id = result['first_id']
    last_id = result['last_id']
    minmax_ret = cursor.execute(QRY_MINMAX_TIME
            .format(dbname=dbname, first_id=first_id, last_id=last_id))
    if minmax_ret != 2:
        raise Exception('Expected 2 results for minmax time query, but got {}.'
                .format(minmax_ret))
    _, min_starttime, _ = cursor.fetchone()
    _, _, max_endtime = cursor.fetchone()
    total_time = max_endtime - min_starttime
    avg_time = total_time / result.num_runs()

    cursor.execute(QRY_REAL_AVG_EXEC_TIME.
            format(dbname=dbname, first_id=first_id, last_id=last_id))
    tmp1, tmp2 = cursor.fetchone()
    real_avg_time = float(tmp1 / 1000)
    real_total_time = float(tmp2 / 1000)

    result['start_time'] = min_starttime
    result['end_time'] = max_endtime
    result['total_time'] = total_time.total_seconds()
    result['avg_time'] = avg_time.total_seconds()
    result['real_avg_time'] = real_avg_time
    result['real_total_time'] = real_total_time

# -----

def count_campaign_set_runs(cursor, dbname):
    """Count how often a campaign set was run and identify the first and last experiment
    run ID that belongs to each campaign set run.

    For sequential experiments, a campaign set consists of exactly one campaign. For
    parallel experiments, a campaign set consists of N campaigns, where N is the degree of
    parallelism, e.g., N=4 means that 4 campaigns are executed in parallel.

    Returns
        a list of the form: [(first_id, last_id), ...]. In other words, a list of tuples
        with the first and the last experiment run ID for each campaign set.
    """

    QRY_TIME_DIFF = '''
        SELECT id, diff
        FROM (
            SELECT
                cur.id,
                TIMESTAMPDIFF({unit}, cur.time, nxt.time) as diff
            FROM
                {dbname}.experiment_run AS cur
            LEFT JOIN
                {dbname}.experiment_run AS nxt
                ON nxt.id = (
                    SELECT MIN(id)
                    FROM {dbname}.experiment_run
                    WHERE id > cur.id
                )
        ) as timediff
        WHERE diff >= {threshold}
    '''

    QRY_MINMAX_RUNID = '''SELECT MIN(id), MAX(id) FROM {dbname}.experiment_run'''

    # do all the DB queries: min and max run id in table and time diffs
    cursor.execute(QRY_MINMAX_RUNID.format(dbname=dbname))
    minmax_runid = cursor.fetchone()
    time_count = cursor.execute(
            QRY_TIME_DIFF.format(dbname=dbname, unit='MINUTE', threshold='20'))

    ret_list = list()
    if time_count == 0:
        ret_list.append(minmax_runid)
    else:
        start_id = minmax_runid[0]
        for (runid, _) in cursor.fetchall():
            ret_list.append((start_id, runid))
            start_id = runid + 1
        ret_list.append((start_id, minmax_runid[1]))

    return ret_list
# -----

def write_ana_results_csv(distribs, outfile, resetfile):
    """Write the result distributions to the CSV file."""

    print('Writing results to [{}]...'.format(outfile))
    if not os.path.exists(outfile):
        resetfile = True
    oflags = 'w' if resetfile else 'a'
    with open(outfile, oflags) as fil:
        writer = csv.DictWriter(fil, AnaResult.csv_order(), extrasaction='ignore')
        if resetfile:
            writer.writeheader()
        writer.writerows(sorted((d.str_dict() for d in distribs), key=lambda d: d['db']))
# -----

def write_ana_result_stats_csv(stats, outfile, resetfile):
    """Write the computed distribution stats to the CSV file.

    Adds '-stats' postfix to filename.
    """

    outfile_split = os.path.splitext(outfile)
    outfile = outfile_split[0] + '-stats' + outfile_split[1]

    print('Writing statistics to [{}]...'.format(outfile))
    if not os.path.exists(outfile):
        resetfile = True
    oflags = 'w' if resetfile else 'a'
    with open(outfile, oflags) as fil:
        writer = csv.DictWriter(fil, AnaResultStats.csv_order(),
                extrasaction='ignore')
        if resetfile:
            writer.writeheader()
        writer.writerows(sorted((s.str_dict() for s in stats), key=lambda s: s['group']))
# -----

def write_accounting_csv(results, outfile, resetfile):
    """Write the computed accounting stats to the CSV file.

    Adds '-acc' postfix to filename.
    """

    outfile_split = os.path.splitext(outfile)
    outfile = outfile_split[0] + '-acc' + outfile_split[1]

    print('Writing accounting data to [{}]...'.format(outfile))
    if not os.path.exists(outfile):
        resetfile = True
    oflags = 'w' if resetfile else 'a'
    with open(outfile, oflags) as fil:
        writer = csv.DictWriter(fil, results[0].acc_data.csv_order(),
                extrasaction='ignore')
        if resetfile:
            writer.writeheader()
        writer.writerows(s.acc_data.str_dict() for s in sorted(results,
            key=lambda s: s['db']))
# -----


def group_experiment_runs(distribs, group_patterns):
    """Group the result distributions according to their DB names and given DB name
    patterns.

    Returns
        a dictionary of the form {<name>: [<distrib>, ...]}, where <name> is the group
        name and <distrib> are the distributions belonging to the group.
    """

    def add_dict_list(dic, key, val):
        if key in dic:
            dic[key].append(val)
        else:
            dic[key] = [val]
    def add_dict_list2(dic, key, lst):
        if key in dic:
            dic[key].extend(lst)
        else:
            dic[key] = lst
    # --

    print('Grouping {} result sets for statistical analysis...'
            .format(len(distribs)))

    # pre-filtering to get unique names and find intra-DB groups
    uniq_names = dict()
    for dis in distribs:
        add_dict_list(uniq_names, dis['db'], dis)

    # actual grouping: intra DB groups are found above, rest is grouped by pattern
    # if no pattern matches for a name, the name is its own group
    groups = dict()
    visited = set()
    for nam, lst in uniq_names.items():
        matched = None
        for pat in group_patterns:
            if re.match(pat, nam):
                if matched:
                    raise Exception('Ambigious group patterns: name [{}] was added '
                            'to group [{}], but may also belong to group [{}].'
                            .format(nam, matched, pat))
                else:
                    add_dict_list2(groups, pat, lst)
                    matched = pat
        if not matched:
            add_dict_list2(groups, nam, lst)

    print('Identified {} groups:'.format(len(groups)))
    for k,v in groups.items():
        print('[{}] with {} members'.format(k, len(v)))

    return groups
# -----

def run_group_stats(groups):
    """Compute statistics for distribution groups, i.e., mean and stddev values"""

    def values(lst, key):
        return [l[key] for l in lst]
    def calc(fnc, dics, keys):
        result = dict()
        for k in keys:
            result[k] = fnc(values(dics, k))
        return result
    # --

    print('Computing statistics for groups...')
    stats = list()
    for grp_name, grp_members in groups.items():
        fields = AnaResult.csv_order_num()
        means = calc(numpy.mean, grp_members, fields)
#        stdev = calc(numpy.std, grp_members, fields)
        tmp = AnaResultStats(group=grp_name)
        tmp.add_means(means)
#        tmp.add_stdevs(stdev)
        stats.append(tmp)
    return stats
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
    db_conn = open_db_connection(args.dbhost, args.dbport, args.dbuser, args.dbpw)
    print()
    db_names = select_db_names(db_conn.cursor(), args.dbnames)
    print()
    ana_results = analyze_experiment_runs(db_conn.cursor(), db_names)
    if not ana_results:
        print("No results found for analysis... exiting")
        exit(1)
    print()
    write_ana_results_csv(ana_results, args.outfile, args.resetfile)
    print()
    write_accounting_csv(ana_results, args.outfile, args.resetfile)
    print()
    run_groups = group_experiment_runs(ana_results, args.run_groups)
    print()
    ana_result_stats = run_group_stats(run_groups)
    print()
    write_ana_result_stats_csv(ana_result_stats, args.outfile, args.resetfile)
    print()

    if args.print_results:
        print('\nPrinting distributions\n-----')
        for r in ana_results:
            print(r.stats())
            print()
        print('-----')

    print('\nFin.')


# -----

if __name__ == '__main__':
    main()


