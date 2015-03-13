#!/usr/bin/python3
"""A Tool to sample, build and register mutants from a file hierarchy."""

import argparse
import mysql.connector
import os
import random
import re
import subprocess

parser = argparse.ArgumentParser(
    description='Select a random sample of available mutants and '
                'insert them into the grinder testcase MySQL table. '
                'Only mutants that compile are selected for the sample. '
                'Note that all C files under <path> are '
                'considered mutants that may be selected.')
parser.add_argument('path', help='Top-level directory of generated mutants.')
parser.add_argument('module_count', metavar='count', type=int,
                    help='Sample size, i.e., number of mutants to select.')
parser.add_argument('--seed', type=int,
                    help='Seed for the PRNG used to select the samples.')
parser.add_argument('--print-list', dest='print_list', action='store_true',
                    help='Print the names of all selected mutant modules '
                    'as newline separated list')
parser.add_argument('--no-db', dest='store_db', action='store_false',
                    help='Do not store the selected mutants in the database.')
parser.add_argument('--no-compile', dest='do_compile', action='store_false',
                    help='Do not build modules as part of the '
                    'selection process. This may lead to the selection of '
                    'non-buildable modules. Usually, you should not use '
                    'this option.')
args = parser.parse_args()

def check_for_old_build_artifacts(files):
    """Check to prevent the inclusion of artefacts of previous builds."""

    for fil in files:
        if fil.endswith('.mod.c'):
            raise RuntimeError(('Detected artifact from previous build [{}]. '
                'Retry after cleaning.').format(fil))
# -----


def compile_mutant_batch(mutant_paths):
    """Compile mutants and return successes and failures.

    The return type is a tuple(list(names), list(names)).

    Only compiles if args.do_compile is set.
    """

    mutants_noext = [os.path.relpath(os.path.splitext(path)[0])
                     for path in mutant_paths]
#    print(mutant_paths)
#    return mutants_noext, []
    if not args.do_compile:
        return mutants_noext, []

    objs = [obj + '.o' for obj in mutants_noext]
    proc = subprocess.Popen(['make', '-f', 'Makefile.mutant',
                             'obj-m=' + ' '.join(objs)],
                            stderr=subprocess.PIPE, universal_newlines=True)
    dead_mutants = []
    for line in proc.stderr:
        print(line)
        failed = compile_mutant_batch.failed_re.match(line)
        if not failed:
            continue
        dead_mutant = os.path.relpath(os.path.splitext(failed.group(1))[0])
        dead_mutants.append(dead_mutant)
        mutants_noext.remove(dead_mutant)
    return [m for m in mutants_noext if m not in dead_mutants], dead_mutants

compile_mutant_batch.failed_re = re.compile(r'make\[\d+\]: \*\*\* '
                                            r'\[(.*)\] Error 1')
# -----


if not args.seed:
    args.seed = int.from_bytes(os.urandom(32), 'big')

print('Using seed {}.'.format(args.seed))
random.seed(args.seed)

# walk mutants directories and collect all C files in a list
print('Enumerating mutants...')
mutants = []
for root, dirs, files in os.walk(args.path):
    dirs.sort()
    files.sort()
    check_for_old_build_artifacts(files)
    mutants.extend([os.path.join(root, fil)
                    for fil in files if fil.endswith('.c')])
mutants_count = len(mutants)
print('Selecting {} out of {} mutants...'.format(args.module_count,
                                                 mutants_count))

selected_mutants = list()
num_skipped = 0
while mutants and len(selected_mutants) < args.module_count:
    # select a batch of mutants
    sample_size = args.module_count - len(selected_mutants)
    # reversed for pop()
    idxs = sorted(random.sample(range(mutants_count), sample_size),
                 reverse=True)
    mutant_batch = [mutants.pop(idx) for idx in idxs]
    mutants_count -= len(idxs)

    # (try to) build them
    print('Trying:\n\t', '\n\t'.join([os.path.split(mutant)[1]
                                for mutant in mutant_batch]))
    new_mutants, failed_mutants = compile_mutant_batch(mutant_batch)

    if new_mutants:
        print('Selected:\n\t', '\n\t'.join([m + '.c' for m in new_mutants]))
        selected_mutants += new_mutants
    if failed_mutants:
        print('Skipped (compile error):\n\t', '\n\t'.join([m + '.c'
                                                     for m in failed_mutants]))
        num_skipped += len(failed_mutants)

print('Finished mutant selection.')
# print('Selected {} mutants, but skipped {}.\n'.format(len(selected_mutants),
#                                                       num_skipped))

if args.print_list:
    print('\n'.join([m + '.ko' for m in selected_mutants]))

if args.do_compile:
    print('Compiling selected mutants.')
    compile_mutant_batch([m + '.c' for m in selected_mutants])
    #print(selected_mutants)

if args.store_db:
    print('Inserting selected mutants into GRINDER database...')
    # TODO: customize via args
    db = mysql.connector.connect(user='grinder', password='grinder',
                                 database='grinder')
    cur = db.cursor()
    values = ["(0, 'none', 0, '{}')".format(os.path.split(mod+'.ko')[1])
              for mod in selected_mutants]
    cur.execute("INSERT INTO testcases "
                "(bit, kservice, parameter, module) "
                "VALUES " + ','.join(values))
    db.commit()

print('Fin.')

