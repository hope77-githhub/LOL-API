from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

# 데이터베이스 접속주소
SQLALCHEMY_DATABASE_URL = 'sqlite:///./loldata.db'

'''
create_engine sessionmaker 는 따라야할 정해진 규칙이지만
autocommit은 잘 알아야한다. autocommit=False 라고 저장하면
commit이라는 사인을 줄 때만 데이터가 저장된다. 그리고 rollback이 가능하다. 
auto commit=True 일 경우에는 rollback 은 작동하지 않는다
'''

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


# base 클래스는 데이터베이스 모델 생성에 쓸 객체이다.

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


