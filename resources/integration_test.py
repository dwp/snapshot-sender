import bz2

input_file = bz2.BZ2File('db.core.addressDeclaration-000001.txt.bz2', 'rb')
try:
    loaded_data = input_file.read()
finally:
    input_file.close()
