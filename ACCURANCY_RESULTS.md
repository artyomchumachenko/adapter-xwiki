Windows PowerShell
(C) Корпорация Майкрософт (Microsoft Corporation). Все права защищены.

Установите последнюю версию PowerShell для новых функций и улучшения! https://aka.ms/PSWindows

PS C:\Users\achumachenko\PycharmProjects\XWikiTools> python evaluate_search.py --csv xwiki_search_eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
C:\Users\achumachenko\AppData\Local\Programs\Python\Python313\python.exe: can't open file 'C:\\Users\\achumachenko\\PycharmProjects\\XWikiTools\\evaluate_search.py': [Errno 2] No such file or directory
PS C:\Users\achumachenko\PycharmProjects\XWikiTools> python evaluate_search.py --csv xwiki_search_eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
C:\Users\achumachenko\AppData\Local\Programs\Python\Python313\python.exe: can't open file 'C:\\Users\\achumachenko\\PycharmProjects\\XWikiTools\\evaluate_search.py': [Errno 2] No such file or directory
PS C:\Users\achumachenko\PycharmProjects\XWikiTools> cd .\testing\
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv xwiki_search_eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
Traceback (most recent call last):
File "C:\Users\achumachenko\PycharmProjects\XWikiTools\testing\evaluate_search.py", line 404, in <module>
main()
~~~~^^
File "C:\Users\achumachenko\PycharmProjects\XWikiTools\testing\evaluate_search.py", line 242, in main
with open(args.csv, "r", encoding="utf-8") as f:
~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
FileNotFoundError: [Errno 2] No such file or directory: 'xwiki_search_eval_dataset.csv'
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5           
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.9700
Hit@5:                               1.0000
nDCG@5:                              0.9775
Linear score@5 (optional):           0.9800

Per-query report:           eval_top5_per_query.csv
Summary JSON:               eval_top5_summary.json
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.9700
Hit@5:                               1.0000
nDCG@5:                              0.9775
Linear score@5 (optional):           0.9800

Rank distribution (exact position of correct page within Top-K):
Rank 1: 57 (95.00%)
Rank 2: 2 (3.33%)
Rank 3: 0 (0.00%)
Rank 4: 0 (0.00%)
Rank 5: 1 (1.67%)
Miss : 0 (0.00%)

Per-query report:           eval_top5_per_query.csv
Summary JSON:               eval_top5_summary.json
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.9700
Hit@5:                               1.0000
nDCG@5:                              0.9775
Linear score@5 (optional):           0.9800

Rank distribution (exact position of correct page within Top-K):
Rank 1: 57 (95.00%)
Rank 2: 2 (3.33%)
Rank 3: 0 (0.00%)
Rank 4: 0 (0.00%)
Rank 5: 1 (1.67%)
Miss : 0 (0.00%)

Per-query report:           out\eval_top5_per_query.csv
Summary JSON:               out\eval_top5_summary.json
Not-top1 debug log:         logs\eval_top5_not_top1.log
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_xwiki_solr.py --csv .\eval_dataset.csv --xwiki-base-url http://localhost:8080 --wiki xwiki --username AAChumachenko --password Onesaz89 --k 5 --sleep 0.05 --out-prefix xwiki_solr_top5 --out-dir out --log-dir logs
=== XWIKI SOLR SEARCH EVALUATION SUMMARY ===
XWiki base URL:            http://localhost:8080
Wiki:                      xwiki
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.4617
Hit@5:                               0.5000
nDCG@5:                              0.4713
Linear score@5 (optional):           0.4767

Rank distribution (exact position of correct page within Top-K):
Rank 1: 26 (43.33%)
Rank 2: 3 (5.00%)
Rank 3: 0 (0.00%)
Rank 4: 0 (0.00%)
Rank 5: 1 (1.67%)
Miss : 30 (50.00%)

Per-query report:          out\xwiki_solr_top5_per_query.csv
Summary JSON:              out\xwiki_solr_top5_summary.json
Not-top1 debug log:        logs\xwiki_solr_top5_not_top1.log
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5                                                                                                       
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.9083
Hit@5:                               0.9667
nDCG@5:                              0.9232
Linear score@5 (optional):           0.9367

Rank distribution (exact position of correct page within Top-K):
Rank 1: 52 (86.67%)
Rank 2: 3 (5.00%)
Rank 3: 3 (5.00%)
Rank 4: 0 (0.00%)
Rank 5: 0 (0.00%)
Miss : 2 (3.33%)

Per-query report:           out\eval_top5_per_query.csv
Summary JSON:               out\eval_top5_summary.json
Not-top1 debug log:         logs\eval_top5_not_top1.log
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.8736
Hit@5:                               0.9500
nDCG@5:                              0.8931
Linear score@5 (optional):           0.9100

Rank distribution (exact position of correct page within Top-K):
Rank 1: 49 (81.67%)
Rank 2: 5 (8.33%)
Rank 3: 2 (3.33%)
Rank 4: 1 (1.67%)
Rank 5: 0 (0.00%)
Miss : 3 (5.00%)

Per-query report:           out\eval_top5_per_query.csv
Summary JSON:               out\eval_top5_summary.json
Not-top1 debug log:         logs\eval_top5_not_top1.log
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.8506
Hit@5:                               0.9500
nDCG@5:                              0.8757
Linear score@5 (optional):           0.8933

Rank distribution (exact position of correct page within Top-K):
Rank 1: 47 (78.33%)
Rank 2: 5 (8.33%)
Rank 3: 4 (6.67%)
Rank 4: 0 (0.00%)
Rank 5: 1 (1.67%)
Miss : 3 (5.00%)

Per-query report:           out\eval_top5_per_query.csv
Summary JSON:               out\eval_top5_summary.json
Not-top1 debug log:         logs\eval_top5_not_top1.log
PS C:\Users\achumachenko\PycharmProjects\XWikiTools\testing> python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
=== SEARCH EVALUATION SUMMARY ===
Base URL:                  http://localhost:8081
Top-K:                     5
Queries evaluated:         60
Successful:                60
Errors:                    0

Positional accuracy@5 (1/rank, MRR): 0.7261
Hit@5:                               0.8667
nDCG@5:                              0.7619
Linear score@5 (optional):           0.7900

Rank distribution (exact position of correct page within Top-K):
Rank 1: 37 (61.67%)
Rank 2: 11 (18.33%)
Rank 3: 2 (3.33%)
Rank 4: 0 (0.00%)
Rank 5: 2 (3.33%)
Miss : 8 (13.33%)

Per-query report:           out\eval_top5_per_query.csv
Summary JSON:               out\eval_top5_summary.json
Not-top1 debug log:         logs\eval_top5_not_top1.log

-----


