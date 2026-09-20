param([Parameter(Mandatory=$true)][string[]]$Commands,[int]$Port=25857,[Parameter(Mandatory=$true)][string]$Password)
$ErrorActionPreference='Stop'
$client=[Net.Sockets.TcpClient]::new();$client.Connect('127.0.0.1',$Port);$client.ReceiveTimeout=10000;$stream=$client.GetStream()
function Read-Exact([int]$Count){
    $bytes=[byte[]]::new($Count);$offset=0
    while($offset-lt$Count){$read=$stream.Read($bytes,$offset,$Count-$offset);if($read-le0){throw 'RCON closed'};$offset+=$read}
    return ,$bytes
}
function Send-Packet([int]$Id,[int]$Type,[string]$Text){
    $payload=[Text.Encoding]::UTF8.GetBytes($Text);$packet=[byte[]]::new($payload.Length+14)
    [BitConverter]::GetBytes($payload.Length+10).CopyTo($packet,0);[BitConverter]::GetBytes($Id).CopyTo($packet,4);[BitConverter]::GetBytes($Type).CopyTo($packet,8);$payload.CopyTo($packet,12);$stream.Write($packet,0,$packet.Length)
}
function Read-Packet{
    $header=Read-Exact 4;$length=[BitConverter]::ToInt32($header,0)
    if($length-lt10-or$length-gt1048576){throw 'Invalid RCON packet'}
    $body=Read-Exact $length
    return @{Id=[BitConverter]::ToInt32($body,0);Type=[BitConverter]::ToInt32($body,4);Text=[Text.Encoding]::UTF8.GetString($body,8,$length-10)}
}
try{
    Send-Packet 1 3 $Password
    do{$reply=Read-Packet;if($reply.Id-eq-1){throw 'RCON authentication rejected'}}while($reply.Type-ne2)
    $id=2
    foreach($command in $Commands){Send-Packet $id 2 $command;do{$reply=Read-Packet}while($reply.Id-ne$id);Write-Output "$command => $($reply.Text)";$id++}
}finally{$stream.Dispose();$client.Dispose()}

