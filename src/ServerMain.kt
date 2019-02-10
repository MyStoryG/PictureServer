import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.util.Calendar
import java.util.Scanner

const val TIMEOUT = 3000
const val ROOT = "C:\\PictureData"

fun main(args: Array<String>) {
    var path: String?
    var readDate: String
    var oldDate: String

    var date: Long
    var currentYear: Int
    var currentMonth: Int
    var previousYear: Int
    var previousMonth: Int
    val receivePort = 1255
    val connectPort = 1256

    val serverLayout = ServerLayout()
    var imageMirroring: ServerLayout.ImageMirroring

    var socket: Socket?
    var serverSocket: ServerSocket

    var dis: DataInputStream
    var dos: DataOutputStream

    var fis: FileInputStream
    var fos: FileOutputStream
    var bos: BufferedOutputStream

    var filePath: File
    var scanner: Scanner?
    var fileCount: Int
    var newDate: ByteArray?


    serverLayout.setPCName()
    serverLayout.setIP()


    loop@ while (true) {
        imageMirroring = serverLayout.ImageMirroring()
        imageMirroring.execute()

        // 1. initialize
        // 1-1. create root folder
        path = ROOT
        filePath = File(path)
        if (!filePath.exists()) {
            filePath.mkdirs()
        }
        serverLayout.mFilePath.text = filePath.absolutePath + "\\"
        // 1-2. check file existing
        path += "\\LASTDATE"
        filePath = File(path)
        if (!filePath.exists()) {
            filePath.createNewFile()
            readDate = "0"
            serverLayout.mLastDate.text = "없음"
        } else {
            // 1-3. get latest date
            fis = FileInputStream(filePath)
            scanner = Scanner(fis)
            if (scanner.hasNext()) {
                readDate = scanner.next()
            } else {
                readDate = "0"
            }
            date = java.lang.Long.parseLong(readDate)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = date
            currentYear = calendar.get(Calendar.YEAR)
            currentMonth = calendar.get(Calendar.MONTH) + 1
//            path = ROOT + "\\$currentYear\\"
//            if (currentMonth < 10) {
//                path += "0"
//            }
//            path += currentMonth.toString() + "\\"
            serverLayout.mLastDate.text = currentYear.toString() + "-" + currentMonth + "-" + calendar.get(Calendar.DATE)
        }
        oldDate = readDate

        // 2. wait client
        // 리스너 소켓 생성 후 대기
        serverSocket = ServerSocket(receivePort)
        println("서버가 시작되었습니다.")
        serverLayout.mConnectInfo.text = "연결 대기중"
        // 연결되면 통신용 소켓 생성
        socket = serverSocket.accept()
        println("클라이언트와 연결되었습니다.")
        serverLayout.mProgressBar.value = 0
        serverLayout.mRemainFiles.text = ""
        serverLayout.mFileName.text = ""

        dis = DataInputStream(socket.getInputStream())
        var clientIp: String? = null
        try {
            clientIp = dis.readUTF()
        } catch (e: SocketException) {
            println("Ping timeout")
            serverSocket.close()
            socket.close()
            dis.close()
            continue
        }

        println("ClientIP $clientIp receive!")
        socket.close()
        dis.close()
        serverSocket.close()

        // IP주소로 reply보내기
        socket = Socket(clientIp, connectPort)
        serverLayout.mConnectInfo.text = "클라이언트 연결 완료"

        dos = DataOutputStream(socket.getOutputStream())
//        val serverIp = InetAddress.getLocalHost().hostAddress
//        dos.writeUTF(serverIp)
//        println("Server IP: $serverIp 전송이 끝났습니다")

        // send last date
        dos.writeUTF(readDate)
        socket.close()
        println("동기화 날짜 전송 완료")

        serverSocket = ServerSocket(connectPort)
        socket = serverSocket.accept()
        dis = DataInputStream(socket.getInputStream())
        // 3. check mode
        val mode = dis.readInt()
        serverLayout.mConnectInfo.text = "파일 수신중..."
        when (mode) {
            0 // sync all mode
            -> {
                previousMonth = 0
                previousYear = 0
                // 파일 개수 받기
                fileCount = dis.readInt()
                println("file count = $fileCount")
                if (fileCount == 0) {
                    break@loop
                }
                serverLayout.mRemainFiles.text = fileCount.toString() + "개 남음.."

                // 설정파일 저장
                newDate = ByteArray(64)
                for (i in 0 until fileCount) {
                    try {
                        socket = serverSocket.accept()
                        serverSocket.soTimeout = TIMEOUT
                        dis = DataInputStream(socket.getInputStream())
                        // 경로 파싱 (날짜 받기)
                        readDate = dis.readUTF() //날짜
                        if (i == 0) {
                            newDate = readDate.toByteArray() //최신사진부터 받기 때문에 처음이 최신날짜
                        }
                    } catch (e: SocketTimeoutException) {
                        println("SocketTimeoutException")
                        newDate = oldDate.toByteArray()
                        break
                    } catch (e: Exception) {
                        println("Exception")
                        newDate = oldDate.toByteArray()
                        break
                    }

                    date = java.lang.Long.parseLong(readDate)
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = date
                    currentYear = calendar.get(Calendar.YEAR)
                    currentMonth = calendar.get(Calendar.MONTH) + 1

                    // 경로 확인
                    if (!(previousMonth == currentMonth && previousYear == currentYear)) { // 다음날짜 폴더 만들기
                        path = ROOT + "\\" + currentYear + "\\"
                        if (currentMonth < 10) {
                            path += "0"
                        }
                        path += currentMonth.toString() + "\\"
                        previousYear = currentYear
                        previousMonth = currentMonth
                    }

                    // 폴더 생성
                    filePath = File(path)
                    if (!filePath.exists()) {
                        filePath.mkdirs()
                    }

                    var fName: String? = null
                    fName = dis.readUTF() //파일 이름

                    println("파일명 [ $fName ] 수신 완료")

                    serverLayout.mFileName.text = fName

                    // 파일을 생성하고 파일에 대한 출력 스트림 생성
                    val file = File(path + fName)
                    fos = FileOutputStream(file)
                    bos = BufferedOutputStream(fos)

                    println("수신 시작")
                    // 받은 파일 크기 측정
                    var len: Int
                    val size = 1500
                    val fileData = ByteArray(size)
                    while (true) {
                        len = dis.read(fileData)
                        var isNext = len == -1
                        if (isNext) {
                            break
                        }
                        bos.write(fileData, 0, len)
                    }

                    println("[ $fName ] 파일 저장 완료")
                    println("받은 파일의 크기 : " + file.length() + " 바이트")

                    imageMirroring.mPath = file.absolutePath
                    System.out.println("m.path = " + imageMirroring.mPath)

                    // set progress bar & remain files
                    val percent = (i + 1) * 100 / fileCount
                    serverLayout.mProgressBar.value = percent
                    if ((fileCount - i - 1) > 0) {
                        serverLayout.mRemainFiles.text = (fileCount - i - 1).toString() + "개 남음.."
                    } else {
                        serverLayout.mRemainFiles.text = "수신 완료"
                    }
                    dis.close()
                    bos.close()
                }
                // 동기화 날짜 업데이트
                filePath = File("$ROOT\\LASTDATE")
                fos = FileOutputStream(filePath)
                bos = BufferedOutputStream(fos)
                newDate?.let { bos.write(it, 0, newDate.size) }
                bos.close()
            }

            1 // sync selected mode
            -> {
                val customFolder = dis.readUTF()
                // 경로 확인
                path = ROOT + "\\" + customFolder + "\\"

                // 폴더 생성
                filePath = File(path)
                if (!filePath.exists()) {
                    filePath.mkdirs()
                }

                // 파일 개수 받기
                fileCount = dis.readInt()
                println("file count = $fileCount")

                if (fileCount == 0) {
                    break@loop
                }
                serverLayout.mRemainFiles.text = fileCount.toString() + "개 남음.."

                for (i in 0 until fileCount) {
                    // socket open
                    var fName: String?
                    try {
                        socket = serverSocket.accept()
                        serverSocket.soTimeout = TIMEOUT
                        dis = DataInputStream(socket.getInputStream())
                        fName = dis.readUTF()
                    } catch (e: SocketTimeoutException) {
                        println("SocketTimeoutException")
                        break
                    } catch (e: Exception) {
                        println("Exception")
                        break
                    }

                    println("파일명 [ $fName ] 수신 완료")

                    serverLayout.mFileName.text = fName

                    // 파일을 생성하고 파일에 대한 출력 스트림 생성
                    val file = fName?.let { File(path + it) }
                    fos = FileOutputStream(file)
                    bos = BufferedOutputStream(fos)

                    println("수신 시작")
                    // 받은 파일 크기 측정
                    var len: Int
                    val size = 1500
                    val fileData = ByteArray(size)

                    while (true) {
                        len = dis.read(fileData)
                        var isNext = len == -1
                        if (isNext) {
                            break
                        }
                        bos.write(fileData, 0, len)
                    }

                    println("[ $fName ] 파일 저장 완료")
                    println("받은 파일의 크기 : " + file?.length() + " 바이트")

                    imageMirroring.mPath = file?.absolutePath
                    System.out.println("m.path = " + imageMirroring.mPath)

                    // set progress bar & remain files
                    val percent = (i + 1) * 100 / fileCount
                    serverLayout.mProgressBar.value = percent
                    if ((fileCount - i - 1) > 0) {
                        serverLayout.mRemainFiles.text = (fileCount - i - 1).toString() + "개 남음.."
                    } else {
                        serverLayout.mRemainFiles.text = "수신 완료"
                    }

                    dis.close()
                    bos.close()
                }
            }
        }
//        serverLayout.mProgressBar.value = 100
//        serverLayout.mRemainFiles.text = "수신 완료"
        if (imageMirroring.isImagePreview()) {
            imageMirroring.stopRunning()
        }
        dis?.close()
        dos?.close()
        serverSocket?.close()
        socket?.close()
    }
}