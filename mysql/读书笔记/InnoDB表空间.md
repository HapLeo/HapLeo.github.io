# InnoDB表空间

表空间就是用来存储表记录的磁盘空间，体现在文件系统上就是一个或多个文件。

表空间分为系统表空间（system tablespace）和独立表空间（file-per-table tablespace），系统表空间有一个或多个文件，默认为数据目录下名为ibdata1的文件,独立表空间是文件名与表名相同，后缀为.ibd的文件，每个表一个文件。

InnoDB以页为单位管理存储空间，页有很多种，包括索引页、BLOB页、undo日志页等。每一种页都有它自己的结构，但所有页都有File Header和File Trailer两个部分。File Header保存了页的校验和、页号、上一页号、下一页号、页面类型、所属表空间等。File Trailer则保存了页的校验和等信息。

## 区 （extent）

