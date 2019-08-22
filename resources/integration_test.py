import bz2
import json
import argparse

# Defaults and test assertion values
total_jsond_lines = 0
expected_value_of_timestamp = 10
expected_number_of_lines = 7

# Obtain filename from command line argument -file
parser = argparse.ArgumentParser()
parser.add_argument('-file', type=str, required=True)
args = parser.parse_args()

# Unzip input_file
input_file = bz2.BZ2File(args.file, 'rb')

# Load input file by line into loaded_data
try:
    loaded_data = input_file.readlines()
finally:
    input_file.close()

# For each line in the loaded_data, check it for expected_value_of_timestamp
for line in loaded_data:
    total_jsond_lines += 1
    jsond_line = json.loads(line)
    assert jsond_line["timestamp"] == expected_value_of_timestamp

# Check total number of lines for 
assert total_jsond_lines == expected_number_of_lines
