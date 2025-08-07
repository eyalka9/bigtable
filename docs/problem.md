This project is a POC between few technical options for the following problem:

# System structure 
Frontend app in react
Backend app in java + springboot

The frontend should have a table with 10000 lines and 40 columns.
The table should support sort, filter and search.
The sort and filter should support multi column sort and filter , with SQL-alike capabilities.
the table data is per session/user and temporary.

We want to investigate the option to solve it by few options:

backend with H2 in memory db
backend with apache arrow

we would like to build and compare the two options with data
we will compare performance and ease of use.
